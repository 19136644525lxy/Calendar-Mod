package com.calendar.mod;

import com.calendar.mod.calendar.CalendarDate;
import com.calendar.mod.client.CalendarClientConfig;
import com.calendar.mod.client.CalendarHud;
import com.calendar.mod.client.CalendarScreen;
import com.calendar.mod.data.CalendarSavedData;
import com.calendar.mod.network.CalendarClientCache;
import com.calendar.mod.network.CalendarConfigPacket;
import com.calendar.mod.network.CalendarEventPacket;
import com.calendar.mod.network.CalendarSyncPacket;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

@Mod(CalendarMod.MOD_ID)
public class CalendarMod {
    public static final String MOD_ID = "calendarmod";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    // 打开日历：默认 C 键
    public static final KeyMapping KEY_OPEN_CALENDAR = new KeyMapping(
            "key.calendarmod.open_calendar",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_C,
            "category.calendarmod.general"
    );

    // 切换日历 HUD：默认 H 键
    public static final KeyMapping KEY_TOGGLE_HUD = new KeyMapping(
            "key.calendarmod.toggle_hud",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            "category.calendarmod.general"
    );

    public CalendarMod(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();
        modBus.addListener(this::clientSetup);
        modBus.addListener(this::onRegisterKeyMappings);

        // 注册 Cloth Config 配置屏幕（Catalogue 会自动识别）
        MinecraftForge.registerConfigScreen(
                parent -> CalendarClientConfig.buildScreen(parent)
        );

        // 加载客户端配置（HUD位置等）
        CalendarClientConfig.load();

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        // 服务端→客户端：日历同步包
        CHANNEL.messageBuilder(CalendarSyncPacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CalendarSyncPacket::encode)
                .decoder(CalendarSyncPacket::decode)
                .consumerMainThread(CalendarSyncPacket::handle)
                .add();
        // 客户端→服务端：历法配置修改包
        CHANNEL.messageBuilder(CalendarConfigPacket.class, 1, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CalendarConfigPacket::encode)
                .decoder(CalendarConfigPacket::decode)
                .consumerMainThread(CalendarConfigPacket::handle)
                .add();
        // 客户端→服务端：事件编辑包
        CHANNEL.messageBuilder(CalendarEventPacket.class, 2, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CalendarEventPacket::encode)
                .decoder(CalendarEventPacket::decode)
                .consumerMainThread(CalendarEventPacket::handle)
                .add();
        LOGGER.info("CalendarMod 初始化完成");
    }

    private void onRegisterKeyMappings(final RegisterKeyMappingsEvent event) {
        event.register(KEY_OPEN_CALENDAR);
        event.register(KEY_TOGGLE_HUD);
    }

    // 玩家登录时从服务端同步日历快照
    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            ServerLevel overworld = sp.serverLevel().getServer().overworld();
            CalendarSavedData data = CalendarSavedData.get(overworld);
            CalendarSyncPacket packet = new CalendarSyncPacket(
                    data.getConfig(),
                    data.getDayZero(),
                    data.getYearOffset(),
                    data.getEvents(),
                    data.getTotalElapsedTicks()
            );
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), packet);
            LOGGER.info("同步日历数据给玩家 {}", sp.getName().getString());
        }
    }

    /**
     * 服务端每 tick 更新实际累计游戏时间 + 检查特殊日期广播。
     */
    @SubscribeEvent
    public void onServerTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.level.isClientSide()) return;
        if (!(event.level instanceof ServerLevel serverLevel)) return;
        if (!serverLevel.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) return;

        CalendarSavedData data = CalendarSavedData.get(serverLevel);
        long gameTime = serverLevel.getGameTime();
        data.onServerTick(gameTime);

        // 检查并广播特殊日期（ActionBar 显示，不走聊天栏）
        String broadcastMsg = data.getTodayEventBroadcast();
        if (broadcastMsg != null && serverLevel.getGameTime() % 20 == 0) {
            net.minecraft.network.chat.Component msg = net.minecraft.network.chat.Component.literal(broadcastMsg);
            for (ServerPlayer sp : serverLevel.getServer().getPlayerList().getPlayers()) {
                sp.displayClientMessage(msg, true);
            }
            data.markBroadcasted();
        }
    }

    // 客户端按键：打开日历界面 / 切换日历 HUD
    @Mod.EventBusSubscriber(modid = MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ClientForgeEvents {
        @SubscribeEvent
        public static void onKeyInput(InputEvent.Key event) {
            if (event.getAction() != GLFW.GLFW_PRESS) return;
            if (KEY_OPEN_CALENDAR.consumeClick()) {
                CalendarScreen screen = new CalendarScreen();
                screen.setClickHandler(screen::handleClick);
                Minecraft.getInstance().setScreen(screen);
            }
            if (KEY_TOGGLE_HUD.consumeClick()) {
                CalendarHud.INSTANCE.toggle();
            }
        }
    }
}
