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
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Calendar Mod 主入口类（Fabric 版，26.2 CustomPacketPayload 适配）。
 * <p>实现 {@link ModInitializer}，在 onInitialize 中完成：
 * <ol>
 *   <li>网络 Payload 注册（3 个：SYNC/CONFIG/EVENT，方向 S2C/C2S/C2S）</li>
 *   <li>服务端 C→S 接收器注册（配置修改包、事件编辑包）</li>
 *   <li>命令注册（/calendar）</li>
 *   <li>服务端 tick 监听（AutoSyncScheduler + 特殊日期广播）</li>
 *   <li>玩家登录同步日历数据</li>
 * </ol>
 *
 * <p>26.2 网络重构要点：
 * <ul>
 *   <li>Payload 必须先通过 {@link PayloadTypeRegistry} 在两端注册 TYPE + STREAM_CODEC</li>
 *   <li>S2C：{@link PayloadTypeRegistry#clientboundPlay()}；C2S：{@link PayloadTypeRegistry#serverboundPlay()}</li>
 *   <li>接收器：{@code ServerPlayNetworking.registerGlobalReceiver(Type<T>, PlayPayloadHandler<T>)}，
 *       handler 形参为 {@code (payload, context)}，context 提供 {@code server()/player()/responseSender()}</li>
 *   <li>发送：{@code ServerPlayNetworking.send(player, payload)}，直接传 payload 实例</li>
 *   <li>权限：{@code player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)}（OP 2+）</li>
 *   <li>ActionBar：{@code sp.sendSystemMessage(msg, true)}（true 表示 ActionBar）</li>
 *   <li>时间：{@code overworld.getGameTime()}（26.2 移除 getTime()，改用 getGameTime()）</li>
 * </ul>
 */
public class CalendarMod implements ModInitializer {
    public static final String MOD_ID = "calendarmod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        // 1. 注册网络 Payload（必须在接收器之前）
        // S2C：服务端→客户端同步包
        PayloadTypeRegistry.clientboundPlay().register(
                CalendarSyncPacket.TYPE, CalendarSyncPacket.STREAM_CODEC);
        // C2S：客户端→服务端配置/事件修改包
        PayloadTypeRegistry.serverboundPlay().register(
                CalendarConfigPacket.TYPE, CalendarConfigPacket.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(
                CalendarEventPacket.TYPE, CalendarEventPacket.STREAM_CODEC);

        // 2. 注册服务端 C→S 接收器（配置修改包）
        // 26.2：handler 在主线程执行，无需手动 server.execute()
        ServerPlayNetworking.registerGlobalReceiver(CalendarConfigPacket.TYPE, (payload, context) -> {
            handleConfigPacket(payload, context.server(), context.player());
        });

        // 3. 注册服务端 C→S 接收器（事件编辑包）
        ServerPlayNetworking.registerGlobalReceiver(CalendarEventPacket.TYPE, (payload, context) -> {
            handleEventPacket(payload, context.server(), context.player());
        });

        // 4. 命令注册（对应 Forge 的 RegisterCommandsEvent）
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            CalendarCommand.register(dispatcher);
        });

        // 5. 服务端 tick：AutoSyncScheduler 日期变更检测 + 特殊日期广播
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            AutoSyncScheduler.onServerTick(server);
            onServerTickForBroadcast(server);
        });

        // 6. 服务器停止时重置 AutoSyncScheduler 状态
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> AutoSyncScheduler.reset());

        // 7. 玩家登录时同步日历数据（对应 Forge 的 PlayerLoggedInEvent）
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            server.execute(() -> onPlayerJoin(player, server));
        });

        LOGGER.info("CalendarMod 初始化完成 (Fabric 26.2)");
    }

    /** 玩家登录：发送日历同步快照 */
    private void onPlayerJoin(ServerPlayer player, MinecraftServer server) {
        var overworld = server.overworld();
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
        var overworld = server.overworld();
        if (overworld == null) return;

        CalendarSavedData data = CalendarSavedData.get(overworld);
        // 26.2：getTime() → getGameTime()
        long gameTime = overworld.getGameTime();
        data.onServerTick(gameTime);

        // 检查并广播特殊日期（ActionBar 显示，不走聊天栏）
        String broadcastMsg = data.getTodayEventBroadcast();
        if (broadcastMsg != null && gameTime % 20 == 0) {
            Component msg = Component.literal(broadcastMsg);
            for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
                sp.sendSystemMessage(msg, true);
            }
            data.markBroadcasted();
        }
    }

    /** 处理客户端→服务端配置修改包（含 OP 权限校验） */
    private void handleConfigPacket(CalendarConfigPacket packet, MinecraftServer server, ServerPlayer player) {
        if (!player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            LOGGER.warn("玩家 {} 无权限修改日历配置", player.getName().getString());
            return;
        }
        var overworld = server.overworld();
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
    private void handleEventPacket(CalendarEventPacket packet, MinecraftServer server, ServerPlayer player) {
        if (!player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            LOGGER.warn("玩家 {} 无权限修改日历事件", player.getName().getString());
            return;
        }
        var overworld = server.overworld();
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
        for (ServerPlayer player : PlayerLookup.all(server)) {
            sendSyncToPlayer(data, player);
        }
    }

    /**
     * 向单个玩家发送日历同步包。
     * 26.2：直接传 payload 实例，无需手动构造 buf。
     */
    public static void sendSyncToPlayer(CalendarSavedData data, ServerPlayer player) {
        CalendarSyncPacket payload = new CalendarSyncPacket(
                data.getConfig(), data.getDayZero(), data.getYearOffset(),
                data.getEvents(), data.getTotalElapsedTicks()
        );
        ServerPlayNetworking.send(player, payload);
    }
}
