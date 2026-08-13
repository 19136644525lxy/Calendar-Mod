package com.calendar.mod;

import com.calendar.mod.command.CalendarCommand;
import com.calendar.mod.data.CalendarSavedData;
import com.calendar.mod.network.CalendarConfigPacket;
import com.calendar.mod.network.CalendarEventPacket;
import com.calendar.mod.network.CalendarSyncPacket;
import com.calendar.mod.server.AutoSyncScheduler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Calendar Mod 主入口类（Fabric 版）。
 * <p>实现 {@link ModInitializer}，在 onInitialize 中完成：
 * <ol>
 *   <li>网络通道注册（3 个 Identifier：SYNC/CONFIG/EVENT）</li>
 *   <li>命令注册（/calendar）</li>
 *   <li>服务端 tick 监听（AutoSyncScheduler）</li>
 *   <li>玩家登录同步日历数据</li>
 *   <li>特殊日期 ActionBar 广播</li>
 * </ol>
 *
 * <p>与 Forge 版差异：
 * <ul>
 *   <li>{@code @Mod} + {@code FMLJavaModLoadingContext} → {@link ModInitializer#onInitialize()}</li>
 *   <li>{@code SimpleChannel} → 每个包类型一个 {@link Identifier} + receiver 注册</li>
 *   <li>{@code @SubscribeEvent PlayerLoggedInEvent} → {@code ServerPlayConnectionEvents.JOIN}</li>
 *   <li>{@code @SubscribeEvent LevelTickEvent} → {@code ServerTickEvents.END_SERVER_TICK}</li>
 *   <li>{@code PacketDistributor.ALL.noArg()} → {@link #broadcastSync}（遍历玩家逐个发送）</li>
 *   <li>{@code PacketDistributor.PLAYER.with(() -> p)} → {@link ServerPlayNetworking#send}</li>
 * </ul>
 */
public class CalendarMod implements ModInitializer {
    public static final String MOD_ID = "calendarmod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // ===== 网络通道 ID（每个包类型一个 Identifier，替代 Forge 的 SimpleChannel 单通道多 id） =====
    /** 服务端→客户端：全量同步 */
    public static final Identifier SYNC_ID = new Identifier(MOD_ID, "sync");
    /** 客户端→服务端：历法配置修改 */
    public static final Identifier CONFIG_ID = new Identifier(MOD_ID, "config");
    /** 客户端→服务端：事件编辑 */
    public static final Identifier EVENT_ID = new Identifier(MOD_ID, "event");

    @Override
    public void onInitialize() {
        // 1. 注册服务端 C→S 接收器（配置修改包）
        ServerPlayNetworking.registerGlobalReceiver(CONFIG_ID, (server, player, handler, buf, responseSender) -> {
            CalendarConfigPacket packet = CalendarConfigPacket.decode(buf);
            // 在主线程执行，避免并发问题（对应 Forge 的 enqueueWork）
            server.execute(() -> handleConfigPacket(packet, server, player));
        });

        // 2. 注册服务端 C→S 接收器（事件编辑包）
        ServerPlayNetworking.registerGlobalReceiver(EVENT_ID, (server, player, handler, buf, responseSender) -> {
            CalendarEventPacket packet = CalendarEventPacket.decode(buf);
            server.execute(() -> handleEventPacket(packet, server, player));
        });

        // 3. 命令注册（对应 Forge 的 RegisterCommandsEvent）
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            CalendarCommand.register(dispatcher);
        });

        // 4. 服务端 tick：AutoSyncScheduler 日期变更检测 + 特殊日期广播
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            AutoSyncScheduler.onServerTick(server);
            onServerTickForBroadcast(server);
        });

        // 5. 服务器停止时重置 AutoSyncScheduler 状态
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> AutoSyncScheduler.reset());

        // 6. 玩家登录时同步日历数据（对应 Forge 的 PlayerLoggedInEvent）
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            server.execute(() -> onPlayerJoin(player, server));
        });

        LOGGER.info("CalendarMod 初始化完成 (Fabric)");
    }

    /** 玩家登录：发送日历同步快照 */
    private void onPlayerJoin(ServerPlayerEntity player, MinecraftServer server) {
        var overworld = server.getOverworld();
        if (overworld == null) return;
        CalendarSavedData data = CalendarSavedData.get(overworld);
        sendSyncToPlayer(data, player);
        LOGGER.info("同步日历数据给玩家 {}", player.getName().getString());
    }

    /**
     * 服务端每 tick：更新累计游戏时间 + 检查特殊日期广播。
     * 对应 Forge 的 onServerTick(LevelTickEvent)。
     */
    private void onServerTickForBroadcast(MinecraftServer server) {
        var overworld = server.getOverworld();
        if (overworld == null) return;

        CalendarSavedData data = CalendarSavedData.get(overworld);
        long gameTime = overworld.getTime();
        data.onServerTick(gameTime);

        // 检查并广播特殊日期（ActionBar 显示，不走聊天栏）
        String broadcastMsg = data.getTodayEventBroadcast();
        if (broadcastMsg != null && gameTime % 20 == 0) {
            Text msg = Text.literal(broadcastMsg);
            for (ServerPlayerEntity sp : server.getPlayerManager().getPlayerList()) {
                sp.sendMessage(msg, true);
            }
            data.markBroadcasted();
        }
    }

    /** 处理客户端→服务端配置修改包（含 OP 权限校验） */
    private void handleConfigPacket(CalendarConfigPacket packet, MinecraftServer server, ServerPlayerEntity player) {
        if (!player.hasPermissionLevel(2)) {
            LOGGER.warn("玩家 {} 无权限修改日历配置", player.getName().getString());
            return;
        }
        var overworld = server.getOverworld();
        if (overworld == null) return;
        CalendarSavedData data = CalendarSavedData.get(overworld);
        data.setConfig(packet.getConfig());
        data.setDayZero(packet.getDayZero());
        data.setYearOffset(packet.getYearOffset());
        LOGGER.info("玩家 {} 修改了日历配置", player.getName().getString());
        // 广播更新给所有在线玩家
        broadcastSync(data, server);
    }

    /** 处理客户端→服务端事件编辑包（含 OP 权限校验） */
    private void handleEventPacket(CalendarEventPacket packet, MinecraftServer server, ServerPlayerEntity player) {
        if (!player.hasPermissionLevel(2)) {
            LOGGER.warn("玩家 {} 无权限修改日历事件", player.getName().getString());
            return;
        }
        var overworld = server.getOverworld();
        if (overworld == null) return;
        CalendarSavedData data = CalendarSavedData.get(overworld);

        if (packet.getAction() == CalendarEventPacket.Action.ADD && packet.getEvent() != null) {
            data.addEvent(packet.getEvent());
            LOGGER.info("玩家 {} 添加了日历事件: {}", player.getName().getString(), packet.getEvent().getName());
        } else if (packet.getAction() == CalendarEventPacket.Action.REMOVE) {
            data.removeEvent(packet.getEventIndex());
            LOGGER.info("玩家 {} 删除了日历事件 #{}", player.getName().getString(), packet.getEventIndex());
        }

        // 广播更新
        broadcastSync(data, server);
    }

    // ===== 网络广播工具方法 =====

    /**
     * 向所有在线玩家广播日历同步包。
     * 替代 Forge 的 {@code PacketDistributor.ALL.noArg()}。
     */
    public static void broadcastSync(CalendarSavedData data, MinecraftServer server) {
        for (ServerPlayerEntity player : PlayerLookup.all(server)) {
            sendSyncToPlayer(data, player);
        }
    }

    /**
     * 向单个玩家发送日历同步包。
     * 替代 Forge 的 {@code PacketDistributor.PLAYER.with(() -> p)}。
     */
    public static void sendSyncToPlayer(CalendarSavedData data, ServerPlayerEntity player) {
        CalendarSyncPacket packet = new CalendarSyncPacket(
                data.getConfig(), data.getDayZero(), data.getYearOffset(),
                data.getEvents(), data.getTotalElapsedTicks()
        );
        PacketByteBuf buf = PacketByteBufs.create();
        CalendarSyncPacket.encode(packet, buf);
        ServerPlayNetworking.send(player, SYNC_ID, buf);
    }
}
