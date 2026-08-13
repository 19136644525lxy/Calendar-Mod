package com.calendar.mod.network;

import com.calendar.mod.data.CalendarEvent;
import com.calendar.mod.CalendarMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 客户端→服务端：事件编辑包（26.2 CustomPacketPayload 版）。
 * 支持添加和删除事件，仅 OP 权限可操作。
 *
 * <p>26.2 重构要点：
 * <ul>
 *   <li>实现 {@link CustomPacketPayload}，提供 {@code TYPE} 与 {@code STREAM_CODEC}</li>
 *   <li>通过 {@link CustomPacketPayload#codec} 构建 {@link StreamCodec}，
 *       复用成员方法 {@code write}/{@code read} 完成序列化</li>
 *   <li>{@code buf.writeEnum(Enum)} 与 {@code buf.readEnum(Class)} 签名不变</li>
 *   <li>{@code buf.readNbt()} 返回 nullable，需做 null 检查</li>
 * </ul>
 */
public class CalendarEventPacket implements CustomPacketPayload {
    /** 唯一通道标识：calendarmod:event */
    public static final Identifier ID = Identifier.fromNamespaceAndPath(CalendarMod.MOD_ID, "event");
    /** Payload 类型对象 */
    public static final CustomPacketPayload.Type<CalendarEventPacket> TYPE = new CustomPacketPayload.Type<>(ID);
    /** 流编解码器 */
    public static final StreamCodec<RegistryFriendlyByteBuf, CalendarEventPacket> STREAM_CODEC =
            CustomPacketPayload.codec(CalendarEventPacket::write, CalendarEventPacket::new);

    public enum Action { ADD, REMOVE }

    private final Action action;
    private final CalendarEvent event;   // ADD 时使用
    private final int eventIndex;        // REMOVE 时使用

    /** 添加事件 */
    public CalendarEventPacket(CalendarEvent event) {
        this.action = Action.ADD;
        this.event = event;
        this.eventIndex = -1;
    }

    /** 删除事件 */
    public CalendarEventPacket(int index) {
        this.action = Action.REMOVE;
        this.event = null;
        this.eventIndex = index;
    }

    /** 从缓冲区反序列化（供 StreamCodec 使用） */
    private CalendarEventPacket(RegistryFriendlyByteBuf buf) {
        this.action = buf.readEnum(Action.class);
        if (this.action == Action.ADD) {
            this.event = CalendarEvent.load(readNbtSafe(buf));
            this.eventIndex = -1;
        } else {
            this.event = null;
            this.eventIndex = buf.readInt();
        }
    }

    /** 序列化到缓冲区（供 StreamCodec 使用） */
    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeEnum(this.action);
        if (this.action == Action.ADD) {
            buf.writeNbt(this.event.save());
        } else {
            buf.writeInt(this.eventIndex);
        }
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

    public Action getAction() { return action; }
    public CalendarEvent getEvent() { return event; }
    public int getEventIndex() { return eventIndex; }
}
