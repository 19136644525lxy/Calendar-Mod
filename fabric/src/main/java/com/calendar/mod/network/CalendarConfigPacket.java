package com.calendar.mod.network;

import com.calendar.mod.calendar.CalendarConfig;
import com.calendar.mod.calendar.CalendarDate;
import net.minecraft.network.PacketByteBuf;

/**
 * 客户端→服务端：历法配置修改包（Fabric/Yarn 版）。
 * 仅 OP 权限的玩家可发送，服务端验证后写入 PersistentState 并广播给所有玩家。
 *
 * <p>与 Forge 版差异：
 * <ul>
 *   <li>{@code FriendlyByteBuf} → {@link PacketByteBuf}</li>
 *   <li>移除 {@code NetworkEvent.Context} handle 逻辑，handle 移至
 *       {@link com.calendar.mod.CalendarMod} 的服务端 receiver 中（SRP）</li>
 * </ul>
 */
public class CalendarConfigPacket {
    private final CalendarConfig config;
    private final CalendarDate dayZero;
    private final int yearOffset;

    public CalendarConfigPacket(CalendarConfig config, CalendarDate dayZero, int yearOffset) {
        this.config = config;
        this.dayZero = dayZero;
        this.yearOffset = yearOffset;
    }

    public static void encode(CalendarConfigPacket packet, PacketByteBuf buf) {
        buf.writeNbt(packet.config.save());
        buf.writeInt(packet.dayZero.getYear());
        buf.writeInt(packet.dayZero.getMonth());
        buf.writeInt(packet.dayZero.getDay());
        buf.writeInt(packet.yearOffset);
    }

    public static CalendarConfigPacket decode(PacketByteBuf buf) {
        CalendarConfig config = CalendarConfig.load(buf.readNbt());
        CalendarDate dayZero = new CalendarDate(buf.readInt(), buf.readInt(), buf.readInt());
        int yearOffset = buf.readInt();
        return new CalendarConfigPacket(config, dayZero, yearOffset);
    }

    public CalendarConfig getConfig() { return config; }
    public CalendarDate getDayZero() { return dayZero; }
    public int getYearOffset() { return yearOffset; }
}
