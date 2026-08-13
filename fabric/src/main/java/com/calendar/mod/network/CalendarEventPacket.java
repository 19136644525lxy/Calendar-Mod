package com.calendar.mod.network;

import com.calendar.mod.data.CalendarEvent;
import net.minecraft.network.PacketByteBuf;

/**
 * 客户端→服务端：事件编辑包（Fabric/Yarn 版）。
 * 支持添加和删除事件，仅 OP 权限可操作。
 *
 * <p>与 Forge 版差异：
 * <ul>
 *   <li>{@code FriendlyByteBuf} → {@link PacketByteBuf}</li>
 *   <li>{@code buf.writeEnum(Action.class)} → {@code buf.writeEnumConstant(Action.class)}</li>
 *   <li>移除 {@code NetworkEvent.Context} handle 逻辑，handle 移至
 *       {@link com.calendar.mod.CalendarMod} 的服务端 receiver 中（SRP）</li>
 * </ul>
 */
public class CalendarEventPacket {
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

    public static void encode(CalendarEventPacket packet, PacketByteBuf buf) {
        // Yarn: writeEnumConstant 替代 Forge 的 writeEnum
        buf.writeEnumConstant(packet.action);
        if (packet.action == Action.ADD) {
            buf.writeNbt(packet.event.save());
        } else {
            buf.writeInt(packet.eventIndex);
        }
    }

    public static CalendarEventPacket decode(PacketByteBuf buf) {
        // Yarn: readEnumConstant 替代 Forge 的 readEnum
        Action action = buf.readEnumConstant(Action.class);
        if (action == Action.ADD) {
            return new CalendarEventPacket(CalendarEvent.load(buf.readNbt()));
        } else {
            return new CalendarEventPacket(buf.readInt());
        }
    }

    public Action getAction() { return action; }
    public CalendarEvent getEvent() { return event; }
    public int getEventIndex() { return eventIndex; }
}
