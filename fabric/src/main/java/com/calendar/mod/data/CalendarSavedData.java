package com.calendar.mod.data;

import com.calendar.mod.calendar.CalendarConfig;
import com.calendar.mod.calendar.CalendarDate;
import com.calendar.mod.calendar.CalendarSystem;
import com.calendar.mod.calendar.ConfigurableCalendar;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

/**
 * 世界级持久化数据（Fabric/Yarn 版）。
 * 继承 {@link PersistentState}（Yarn 中对应 Forge 的 SavedData），
 * 存储配置/事件/累计 tick，每 tick 记录单调递增的游戏时间。
 *
 * <p>与 Forge 版差异：
 * <ul>
 *   <li>{@code SavedData} → {@link PersistentState}</li>
 *   <li>{@code save(CompoundTag)} → {@link #writeNbt(NbtCompound)}</li>
 *   <li>{@code setDirty()} → {@link #markDirty()}</li>
 *   <li>{@code level.getDataStorage()} → {@code world.getPersistentStateManager()}</li>
 *   <li>{@code computeIfAbsent(load, new, name)} → {@code getOrCreate(type, name)}</li>
 *   <li>NBT 类名全部替换为 Yarn（CompoundTag→NbtCompound 等）</li>
 * </ul>
 */
public class CalendarSavedData extends PersistentState {
    private static final String DATA_NAME = "calendar_data";

    private CalendarConfig config = new CalendarConfig();
    private CalendarDate dayZero = new CalendarDate(1, 1, 1);
    private final List<CalendarEvent> events = new CopyOnWriteArrayList<>();
    private int yearOffset = 0;

    /**
     * 实际累计游戏刻数（不受 /time 指令影响）。
     * 每当玩家进入存档并游戏时，此值单调递增，永不回退。
     * 初始值为 0，每个新存档从第 0 天开始计时。
     */
    private long totalElapsedTicks = 0;

    /**
     * 上一次记录的服务器游戏时间（用于计算增量）。
     * 当游戏时间减少时（如使用 /time set），不会减少 totalElapsedTicks。
     */
    private long lastServerGameTime = -1;

    /** 上一次广播特殊日期的 worldDay（同一天只广播一次） */
    private long lastBroadcastWorldDay = -1;

    public CalendarSavedData() {}

    /** 从 NBT 反序列化 */
    public static CalendarSavedData loadFromNbt(NbtCompound tag) {
        CalendarSavedData data = new CalendarSavedData();
        if (tag.contains("config", NbtElement.COMPOUND_TYPE)) {
            data.config = CalendarConfig.load(tag.getCompound("config"));
        }
        if (tag.contains("dayZero", NbtElement.COMPOUND_TYPE)) {
            NbtCompound dz = tag.getCompound("dayZero");
            data.dayZero = new CalendarDate(dz.getInt("year"), dz.getInt("month"), dz.getInt("day"));
        }
        data.yearOffset = tag.getInt("yearOffset");

        // 加载累计游戏时间（兼容旧存档：没有此字段时默认为 0）
        data.totalElapsedTicks = tag.getLong("totalElapsedTicks");
        data.lastBroadcastWorldDay = tag.getLong("lastBroadcastWorldDay");

        if (tag.contains("events", NbtElement.LIST_TYPE)) {
            NbtList list = tag.getList("events", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < list.size(); i++) {
                data.events.add(CalendarEvent.load(list.getCompound(i)));
            }
        }
        return data;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound tag) {
        tag.put("config", config.save());
        NbtCompound dz = new NbtCompound();
        dz.putInt("year", dayZero.getYear());
        dz.putInt("month", dayZero.getMonth());
        dz.putInt("day", dayZero.getDay());
        tag.put("dayZero", dz);
        tag.putInt("yearOffset", yearOffset);
        // 保存累计游戏时间
        tag.putLong("totalElapsedTicks", totalElapsedTicks);
        tag.putLong("lastBroadcastWorldDay", lastBroadcastWorldDay);
        NbtList list = new NbtList();
        for (CalendarEvent event : events) {
            list.add(event.save());
        }
        tag.put("events", list);
        return tag;
    }

    /** 获取当前世界的日历数据（通过 PersistentStateManager 加载或创建） */
    public static CalendarSavedData get(ServerWorld world) {
        PersistentStateManager manager = world.getPersistentStateManager();
        // Yarn 1.20.1: getOrCreate(readFunction, supplier, id)
        CalendarSavedData data = manager.getOrCreate(
                CalendarSavedData::loadFromNbt,   // nbt 读取器
                CalendarSavedData::new,           // supplier（新建空对象）
                DATA_NAME);
        return data;
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
            markDirty();
            return;
        }
        long delta = serverGameTime - lastServerGameTime;
        if (delta > 0) {
            // 正常流逝时间
            totalElapsedTicks += delta;
            markDirty();
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

    public void addEvent(CalendarEvent event) { events.add(event); markDirty(); }

    public void removeEvent(int index) {
        if (index >= 0 && index < events.size()) {
            events.remove(index);
            markDirty();
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
    public void setYearOffset(int yearOffset) { this.yearOffset = yearOffset; markDirty(); }

    public CalendarDate getDayZero() { return dayZero; }
    public void setDayZero(CalendarDate dayZero) { this.dayZero = dayZero; markDirty(); }

    public CalendarConfig getConfig() { return config; }
    public void setConfig(CalendarConfig config) { this.config = config; markDirty(); }

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
        markDirty();
    }
}
