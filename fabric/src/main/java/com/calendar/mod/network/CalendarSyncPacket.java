package com.calendar.mod.network;

import com.calendar.mod.calendar.CalendarConfig;
import com.calendar.mod.calendar.CalendarDate;
import com.calendar.mod.data.CalendarEvent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.PacketByteBuf;

/**
 * 服务端→客户端日历数据同步包（Fabric/Yarn 版）。
 * 包含完整的历法配置 + 事件列表 + 实际累计游戏时间。
 *
 * <p>与 Forge 版差异：
 * <ul>
 *   <li>{@code FriendlyByteBuf} → {@link PacketByteBuf}</li>
 *   <li>移除 {@code NetworkEvent.Context} handle 逻辑，handle 移至
 *       {@link com.calendar.mod.CalendarModClient} 的客户端 receiver 中（SRP）</li>
 *   <li>encode/decode 方法签名不变，仅参数类型替换</li>
 * </ul>
 */
public class CalendarSyncPacket {
    private final CalendarConfig config;
    private final CalendarDate dayZero;
    private final int yearOffset;
    private final List<CalendarEvent> events;
    private final long totalElapsedTicks;

    public CalendarSyncPacket(CalendarConfig config, CalendarDate dayZero, int yearOffset,
                              List<CalendarEvent> events, long totalElapsedTicks) {
        this.config = config;
        this.dayZero = dayZero;
        this.yearOffset = yearOffset;
        this.events = new ArrayList<>(events);
        this.totalElapsedTicks = totalElapsedTicks;
    }

    public static void encode(CalendarSyncPacket packet, PacketByteBuf buf) {
        buf.writeNbt(packet.config.save());
        buf.writeInt(packet.dayZero.getYear());
        buf.writeInt(packet.dayZero.getMonth());
        buf.writeInt(packet.dayZero.getDay());
        buf.writeInt(packet.yearOffset);
        buf.writeInt(packet.events.size());
        for (CalendarEvent event : packet.events) {
            buf.writeNbt(event.save());
        }
        buf.writeLong(packet.totalElapsedTicks);
    }

    public static CalendarSyncPacket decode(PacketByteBuf buf) {
        CalendarConfig config = CalendarConfig.load(buf.readNbt());
        CalendarDate dayZero = new CalendarDate(buf.readInt(), buf.readInt(), buf.readInt());
        int yearOffset = buf.readInt();
        int size = buf.readInt();
        List<CalendarEvent> events = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            events.add(CalendarEvent.load(buf.readNbt()));
        }
        long totalElapsedTicks = buf.readLong();
        return new CalendarSyncPacket(config, dayZero, yearOffset, events, totalElapsedTicks);
    }

    public CalendarConfig getConfig() { return config; }
    public CalendarDate getDayZero() { return dayZero; }
    public int getYearOffset() { return yearOffset; }
    public List<CalendarEvent> getEvents() { return events; }
    public long getTotalElapsedTicks() { return totalElapsedTicks; }
}
