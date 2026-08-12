package com.calendar.mod.network;

import com.calendar.mod.calendar.CalendarConfig;
import com.calendar.mod.calendar.CalendarDate;
import com.calendar.mod.data.CalendarEvent;
import com.calendar.mod.data.CalendarSavedData;
import com.mojang.logging.LogUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 客户端→服务端：历法配置修改包。
 * 仅 OP 权限的玩家可发送，服务端验证后写入 SavedData 并广播给所有玩家。
 */
public class CalendarConfigPacket {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final CalendarConfig config;
    private final CalendarDate dayZero;
    private final int yearOffset;

    public CalendarConfigPacket(CalendarConfig config, CalendarDate dayZero, int yearOffset) {
        this.config = config;
        this.dayZero = dayZero;
        this.yearOffset = yearOffset;
    }

    public static void encode(CalendarConfigPacket packet, FriendlyByteBuf buf) {
        buf.writeNbt(packet.config.save());
        buf.writeInt(packet.dayZero.getYear());
        buf.writeInt(packet.dayZero.getMonth());
        buf.writeInt(packet.dayZero.getDay());
        buf.writeInt(packet.yearOffset);
    }

    public static CalendarConfigPacket decode(FriendlyByteBuf buf) {
        CalendarConfig config = CalendarConfig.load(buf.readNbt());
        CalendarDate dayZero = new CalendarDate(buf.readInt(), buf.readInt(), buf.readInt());
        int yearOffset = buf.readInt();
        return new CalendarConfigPacket(config, dayZero, yearOffset);
    }

    public static void handle(CalendarConfigPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            // 权限校验：仅 OP（权限等级 2+）可修改历法配置
            if (!player.hasPermissions(2)) {
                LOGGER.warn("玩家 {} 无权限修改日历配置", player.getName().getString());
                ctx.setPacketHandled(true);
                return;
            }
            MinecraftServer server = player.getServer();
            if (server == null) return;
            ServerLevel overworld = server.overworld();
            CalendarSavedData data = CalendarSavedData.get(overworld);
            data.setConfig(packet.config);
            data.setDayZero(packet.dayZero);
            data.setYearOffset(packet.yearOffset);
            data.setDirty();
            LOGGER.info("玩家 {} 修改了日历配置", player.getName().getString());
            // 广播更新给所有在线玩家
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
