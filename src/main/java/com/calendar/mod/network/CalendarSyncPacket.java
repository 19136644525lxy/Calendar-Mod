package com.calendar.mod.network;

import com.calendar.mod.calendar.CalendarConfig;
import com.calendar.mod.calendar.CalendarDate;
import com.calendar.mod.data.CalendarEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * 服务端→客户端日历数据同步包。
 * 包含完整的历法配置 + 事件列表 + 实际累计游戏时间。
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

    public static void encode(CalendarSyncPacket packet, FriendlyByteBuf buf) {
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

    public static CalendarSyncPacket decode(FriendlyByteBuf buf) {
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

    public static void handle(CalendarSyncPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            CalendarClientCache.INSTANCE.update(packet.config, packet.dayZero,
                    packet.yearOffset, packet.events, packet.totalElapsedTicks);
        });
        context.setPacketHandled(true);
    }

    public CalendarConfig getConfig() { return config; }
    public CalendarDate getDayZero() { return dayZero; }
    public int getYearOffset() { return yearOffset; }
    public List<CalendarEvent> getEvents() { return events; }
    public long getTotalElapsedTicks() { return totalElapsedTicks; }
}
