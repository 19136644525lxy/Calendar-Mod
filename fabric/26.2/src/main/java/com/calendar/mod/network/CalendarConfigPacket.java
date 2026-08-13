package com.calendar.mod.network;

import com.calendar.mod.calendar.CalendarConfig;
import com.calendar.mod.calendar.CalendarDate;
import com.calendar.mod.CalendarMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 客户端→服务端：历法配置修改包（26.2 CustomPacketPayload 版）。
 * 仅 OP 权限的玩家可发送，服务端验证后写入 SavedData 并广播给所有玩家。
 *
 * <p>26.2 重构要点：
 * <ul>
 *   <li>实现 {@link CustomPacketPayload}，提供 {@code TYPE} 与 {@code STREAM_CODEC}</li>
 *   <li>通过 {@link CustomPacketPayload#codec} 构建 {@link StreamCodec}，
 *       复用成员方法 {@code write}/{@code read} 完成序列化</li>
 *   <li>发送方：{@code ClientPlayNetworking.send(packet)} 直接传 payload</li>
 *   <li>接收方：{@code ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, ctx) -> ...)}</li>
 * </ul>
 */
public class CalendarConfigPacket implements CustomPacketPayload {
    /** 唯一通道标识：calendarmod:config */
    public static final Identifier ID = Identifier.fromNamespaceAndPath(CalendarMod.MOD_ID, "config");
    /** Payload 类型对象 */
    public static final CustomPacketPayload.Type<CalendarConfigPacket> TYPE = new CustomPacketPayload.Type<>(ID);
    /** 流编解码器 */
    public static final StreamCodec<RegistryFriendlyByteBuf, CalendarConfigPacket> STREAM_CODEC =
            CustomPacketPayload.codec(CalendarConfigPacket::write, CalendarConfigPacket::new);

    private final CalendarConfig config;
    private final CalendarDate dayZero;
    private final int yearOffset;

    public CalendarConfigPacket(CalendarConfig config, CalendarDate dayZero, int yearOffset) {
        this.config = config;
        this.dayZero = dayZero;
        this.yearOffset = yearOffset;
    }

    /** 从缓冲区反序列化（供 StreamCodec 使用） */
    private CalendarConfigPacket(RegistryFriendlyByteBuf buf) {
        this.config = CalendarConfig.load(readNbtSafe(buf));
        this.dayZero = new CalendarDate(buf.readInt(), buf.readInt(), buf.readInt());
        this.yearOffset = buf.readInt();
    }

    /** 序列化到缓冲区（供 StreamCodec 使用） */
    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeNbt(this.config.save());
        buf.writeInt(this.dayZero.getYear());
        buf.writeInt(this.dayZero.getMonth());
        buf.writeInt(this.dayZero.getDay());
        buf.writeInt(this.yearOffset);
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
}
