package com.calendar.mod.network;

import com.calendar.mod.data.CalendarEvent;
import com.calendar.mod.data.CalendarSavedData;
import com.mojang.logging.LogUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;

import java.util.function.Supplier;

/**
 * 客户端→服务端：事件编辑包。
 * 支持添加和删除事件，仅 OP 权限可操作。
 */
public class CalendarEventPacket {
    private static final Logger LOGGER = LogUtils.getLogger();

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

    public static void encode(CalendarEventPacket packet, FriendlyByteBuf buf) {
        buf.writeEnum(packet.action);
        if (packet.action == Action.ADD) {
            buf.writeNbt(packet.event.save());
        } else {
            buf.writeInt(packet.eventIndex);
        }
    }

    public static CalendarEventPacket decode(FriendlyByteBuf buf) {
        Action action = buf.readEnum(Action.class);
        if (action == Action.ADD) {
            return new CalendarEventPacket(CalendarEvent.load(buf.readNbt()));
        } else {
            return new CalendarEventPacket(buf.readInt());
        }
    }

    public static void handle(CalendarEventPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            if (!player.hasPermissions(2)) {
                LOGGER.warn("玩家 {} 无权限修改日历事件", player.getName().getString());
                ctx.setPacketHandled(true);
                return;
            }
            MinecraftServer server = player.getServer();
            if (server == null) return;
            ServerLevel overworld = server.overworld();
            CalendarSavedData data = CalendarSavedData.get(overworld);

            if (packet.action == Action.ADD && packet.event != null) {
                data.addEvent(packet.event);
                LOGGER.info("玩家 {} 添加了日历事件: {}", player.getName().getString(), packet.event.getName());
            } else if (packet.action == Action.REMOVE) {
                data.removeEvent(packet.eventIndex);
                LOGGER.info("玩家 {} 删除了日历事件 #{}", player.getName().getString(), packet.eventIndex);
            }

            // 广播更新
            CalendarSyncPacket syncPacket = new CalendarSyncPacket(
                    data.getConfig(), data.getDayZero(), data.getYearOffset(),
                    data.getEvents(), data.getTotalElapsedTicks()
            );
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                com.calendar.mod.CalendarMod.CHANNEL.send(
                        net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p),
                        syncPacket
                );
            }
        });
        ctx.setPacketHandled(true);
    }
}
