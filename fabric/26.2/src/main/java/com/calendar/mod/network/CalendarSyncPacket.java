package com.calendar.mod.network;

import com.calendar.mod.calendar.CalendarConfig;
import com.calendar.mod.calendar.CalendarDate;
import com.calendar.mod.data.CalendarEvent;
import com.calendar.mod.CalendarMod;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 服务端→客户端日历数据同步包（26.2 CustomPacketPayload 版）。
 * 包含完整的历法配置 + 事件列表 + 实际累计游戏时间。
 *
 * <p>26.2 重构要点：
 * <ul>
 *   <li>实现 {@link CustomPacketPayload}，提供 {@code TYPE} 与 {@code STREAM_CODEC}</li>
 *   <li>通过 {@link CustomPacketPayload#codec} 构建 {@link StreamCodec}，
 *       复用成员方法 {@code write}/{@code read} 完成序列化</li>
 *   <li>发送方：{@code ServerPlayNetworking.send(player, packet)} 直接传 payload</li>
 *   <li>接收方：{@code ClientPlayNetworking.registerGlobalReceiver(TYPE, (payload, ctx) -> ...)}</li>
 *   <li>{@code buf.readNbt()} 返回 nullable，需做 null 检查</li>
 * </ul>
 */
public class CalendarSyncPacket implements CustomPacketPayload {
    /** 唯一通道标识：calendarmod:sync */
    public static final Identifier ID = Identifier.fromNamespaceAndPath(CalendarMod.MOD_ID, "sync");
    /** Payload 类型对象，用于注册与接收器绑定 */
    public static final CustomPacketPayload.Type<CalendarSyncPacket> TYPE = new CustomPacketPayload.Type<>(ID);
    /** 流编解码器：复用 write/read 方法 */
    public static final StreamCodec<RegistryFriendlyByteBuf, CalendarSyncPacket> STREAM_CODEC =
            CustomPacketPayload.codec(CalendarSyncPacket::write, CalendarSyncPacket::new);

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

    /** 从缓冲区反序列化（供 StreamCodec 使用） */
    private CalendarSyncPacket(RegistryFriendlyByteBuf buf) {
        this.config = CalendarConfig.load(readNbtSafe(buf));
        this.dayZero = new CalendarDate(buf.readInt(), buf.readInt(), buf.readInt());
        this.yearOffset = buf.readInt();
        int size = buf.readInt();
        List<CalendarEvent> evts = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            evts.add(CalendarEvent.load(readNbtSafe(buf)));
        }
        this.events = evts;
        this.totalElapsedTicks = buf.readLong();
    }

    /** 序列化到缓冲区（供 StreamCodec 使用） */
    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeNbt(this.config.save());
        buf.writeInt(this.dayZero.getYear());
        buf.writeInt(this.dayZero.getMonth());
        buf.writeInt(this.dayZero.getDay());
        buf.writeInt(this.yearOffset);
        buf.writeInt(this.events.size());
        for (CalendarEvent event : this.events) {
            buf.writeNbt(event.save());
        }
        buf.writeLong(this.totalElapsedTicks);
    }

    /** 安全读取 NBT：26.2 readNbt 返回 nullable，空时回退空 CompoundTag */
    private static net.minecraft.nbt.CompoundTag readNbtSafe(FriendlyByteBuf buf) {
        net.minecraft.nbt.CompoundTag tag = buf.readNbt();
        return tag != null ? tag : new net.minecraft.nbt.CompoundTag();
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public CalendarConfig getConfig() { return config; }
    public CalendarDate getDayZero() { return dayZero; }
    public int getYearOffset() { return yearOffset; }
    public List<CalendarEvent> getEvents() { return events; }
    public long getTotalElapsedTicks() { return totalElapsedTicks; }
}
