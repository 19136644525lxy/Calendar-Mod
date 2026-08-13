package com.calendar.mod.data;

import com.calendar.mod.calendar.CalendarConfig;
import com.calendar.mod.calendar.CalendarDate;
import com.calendar.mod.calendar.CalendarSystem;
import com.calendar.mod.calendar.ConfigurableCalendar;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * 世界级持久化数据（26.2/Mojang 映射版）。
 * 继承 {@link SavedData}，使用 {@link Codec} + {@link SavedDataType} 模式序列化，
 * 存储配置/事件/累计 tick，每 tick 记录单调递增的游戏时间。
 *
 * <p>26.2 重构要点：
 * <ul>
 *   <li>{@code PersistentState} → {@link SavedData}</li>
 *   <li>{@code writeNbt(NbtCompound)} → {@link Codec} 序列化</li>
 *   <li>{@code markDirty()} → {@link #setDirty()}</li>
 *   <li>{@code manager.getOrCreate(...)} → {@code level.getDataStorage().computeIfAbsent(TYPE)}</li>
 *   <li>NBT 类名全部使用 Mojang 映射（CompoundTag 等）</li>
 *   <li>复杂对象（CalendarConfig/CalendarEvent）通过 {@link CompoundTag#CODEC} 保留原 NBT 序列化</li>
 * </ul>
 */
public class CalendarSavedData extends SavedData {

    /**
     * Codec：将所有持久化字段序列化为 NBT。
     * 复杂对象（config/dayZero/events）通过 CompoundTag.CODEC 包装，复用各自的 save/load 方法，
     * 保证与旧版 NBT 格式完全一致。
     */
    public static final Codec<CalendarSavedData> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            CompoundTag.CODEC.optionalFieldOf("config", new CompoundTag())
                .forGetter(d -> d.config.save()),
            CompoundTag.CODEC.optionalFieldOf("dayZero", new CompoundTag())
                .forGetter(d -> saveDayZero(d.dayZero)),
            Codec.INT.optionalFieldOf("yearOffset", 0)
                .forGetter(d -> d.yearOffset),
            Codec.LONG.optionalFieldOf("totalElapsedTicks", 0L)
                .forGetter(d -> d.totalElapsedTicks),
            Codec.LONG.optionalFieldOf("lastBroadcastWorldDay", 0L)
                .forGetter(d -> d.lastBroadcastWorldDay),
            CompoundTag.CODEC.listOf().optionalFieldOf("events", List.<CompoundTag>of())
                .forGetter(d -> saveEvents(d.events))
        ).apply(instance, CalendarSavedData::new)
    );

    /** SavedData 类型定义：ID + 空实例工厂 + Codec + DataFixType（null） */
    public static final SavedDataType<CalendarSavedData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath("calendarmod", "calendar_data"),
        CalendarSavedData::new,
        CODEC,
        null
    );

    private CalendarConfig config;
    private CalendarDate dayZero;
    private final List<CalendarEvent> events;
    private int yearOffset;

    /**
     * 实际累计游戏刻数（不受 /time 指令影响）。
     * 每当玩家进入存档并游戏时，此值单调递增，永不回退。
     * 初始值为 0，每个新存档从第 0 天开始计时。
     */
    private long totalElapsedTicks;

    /**
     * 上一次记录的服务器游戏时间（用于计算增量）。
     * 当游戏时间减少时（如使用 /time set），不会减少 totalElapsedTicks。
     * 不参与持久化，每次加载后重置为 -1。
     */
    private long lastServerGameTime;

    /** 上一次广播特殊日期的 worldDay（同一天只广播一次） */
    private long lastBroadcastWorldDay;

    /** 空实例构造（新建存档时由工厂调用） */
    public CalendarSavedData() {
        this.config = new CalendarConfig();
        this.dayZero = new CalendarDate(1, 1, 1);
        this.events = new CopyOnWriteArrayList<>();
        this.yearOffset = 0;
        this.totalElapsedTicks = 0;
        this.lastServerGameTime = -1;
        this.lastBroadcastWorldDay = -1;
    }

    /**
     * Codec 反序列化构造：从各字段的 CompoundTag/标量恢复实例。
     * lastServerGameTime 不持久化，重置为 -1。
     */
    private CalendarSavedData(CompoundTag configTag, CompoundTag dayZeroTag,
                              int yearOffset, long totalElapsedTicks,
                              long lastBroadcastWorldDay, List<CompoundTag> eventTags) {
        this.config = CalendarConfig.load(configTag);
        if (dayZeroTag.contains("year")) {
            this.dayZero = new CalendarDate(
                dayZeroTag.getIntOr("year", 0),
                dayZeroTag.getIntOr("month", 0),
                dayZeroTag.getIntOr("day", 0));
        } else {
            this.dayZero = new CalendarDate(1, 1, 1);
        }
        CopyOnWriteArrayList<CalendarEvent> evts = new CopyOnWriteArrayList<>();
        for (CompoundTag eventTag : eventTags) {
            evts.add(CalendarEvent.load(eventTag));
        }
        this.events = evts;
        this.yearOffset = yearOffset;
        this.totalElapsedTicks = totalElapsedTicks;
        this.lastServerGameTime = -1;
        this.lastBroadcastWorldDay = lastBroadcastWorldDay;
    }

    /** 将 dayZero 序列化为 CompoundTag（供 Codec 使用） */
    private static CompoundTag saveDayZero(CalendarDate date) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("year", date.getYear());
        tag.putInt("month", date.getMonth());
        tag.putInt("day", date.getDay());
        return tag;
    }

    /** 将事件列表序列化为 CompoundTag 列表（供 Codec 使用） */
    private static List<CompoundTag> saveEvents(List<CalendarEvent> events) {
        List<CompoundTag> list = new ArrayList<>();
        for (CalendarEvent event : events) {
            list.add(event.save());
        }
        return list;
    }

    /** 获取当前世界的日历数据（通过 DataStorage 加载或创建） */
    public static CalendarSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    /**
     * 由服务器每 tick 调用，记录实际流逝的游戏时间。
     * 单调递增：若检测到游戏时间回退（/time set），保持原累计值不变。
     *
     * @param serverGameTime 当前服务器的 getTime()（当前层级的累计 tick）
     */
    public void onServerTick(long serverGameTime) {
        if (lastServerGameTime < 0) {
            // 首次记录
            lastServerGameTime = serverGameTime;
            setDirty();
            return;
        }
        long delta = serverGameTime - lastServerGameTime;
        if (delta > 0) {
            // 正常流逝时间
            totalElapsedTicks += delta;
            setDirty();
        }
        // delta <= 0 说明时间被重置或回退，不更新 totalElapsedTicks
        lastServerGameTime = serverGameTime;
    }

    /**
     * 基于实际累计游戏时间计算当前日期。
     * 与 /time 指令无关，仅受实际游戏时长影响。
     */
    public CalendarDate getCurrentDate() {
        long worldDay = totalElapsedTicks / 24000L;
        CalendarSystem sys = new ConfigurableCalendar(config);
        CalendarDate base = sys.fromWorldDays(worldDay, dayZero);
        return new CalendarDate(base.getYear() + yearOffset, base.getMonth(), base.getDay());
    }

    /**
     * 获取实际累计游戏天数（用于调试或显示）。
     */
    public long getElapsedWorldDays() {
        return totalElapsedTicks / 24000L;
    }

    public long getTotalElapsedTicks() {
        return totalElapsedTicks;
    }

    public List<CalendarEvent> getEvents() { return events; }

    public void addEvent(CalendarEvent event) { events.add(event); setDirty(); }

    public void removeEvent(int index) {
        if (index >= 0 && index < events.size()) {
            events.remove(index);
            setDirty();
        }
    }

    public List<CalendarEvent> getEventsForDate(CalendarDate date) {
        List<CalendarEvent> result = new ArrayList<>();
        for (CalendarEvent event : events) {
            CalendarDate ed = event.date();
            if (event.isFixed()) {
                if (ed.getMonth() == date.getMonth() && ed.getDay() == date.getDay()) {
                    result.add(event);
                }
            } else {
                if (ed.equals(date)) result.add(event);
            }
        }
        return result;
    }

    // ===== Getters & Setters =====

    public int getYearOffset() { return yearOffset; }
    public void setYearOffset(int yearOffset) { this.yearOffset = yearOffset; setDirty(); }

    public CalendarDate getDayZero() { return dayZero; }
    public void setDayZero(CalendarDate dayZero) { this.dayZero = dayZero; setDirty(); }

    public CalendarConfig getConfig() { return config; }
    public void setConfig(CalendarConfig config) { this.config = config; setDirty(); }

    public CalendarSystem getCalendarSystem() { return new ConfigurableCalendar(config); }

    /**
     * 获取今日事件的广播消息。
     * 仅当今日事件尚未广播过时返回消息，否则返回 null。
     * @return 广播消息文本，或 null（无事件或已广播）
     */
    public String getTodayEventBroadcast() {
        long worldDay = totalElapsedTicks / 24000L;
        if (worldDay == lastBroadcastWorldDay) return null;

        CalendarDate today = getCurrentDate();
        List<CalendarEvent> todayEvents = getEventsForDate(today);
        if (todayEvents.isEmpty()) return null;

        StringBuilder sb = new StringBuilder();
        sb.append("📅 ");
        sb.append(config.getEraName()).append(" ");
        sb.append(today.getYear()).append(" ");
        sb.append(config.getMonthName(today.getMonth())).append(" ");
        sb.append(today.getDay()).append("日");
        if (todayEvents.size() == 1) {
            sb.append(" · ").append(todayEvents.get(0).getName());
        } else {
            sb.append(" · 共 ").append(todayEvents.size()).append(" 个事件");
        }
        return sb.toString();
    }

    /** 标记今日已广播，防止重复广播 */
    public void markBroadcasted() {
        lastBroadcastWorldDay = totalElapsedTicks / 24000L;
        setDirty();
    }
}
