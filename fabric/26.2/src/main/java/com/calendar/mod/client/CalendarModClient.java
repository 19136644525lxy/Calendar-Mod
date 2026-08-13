package com.calendar.mod.client;

import com.calendar.mod.CalendarMod;
import com.calendar.mod.network.CalendarClientCache;
import com.calendar.mod.network.CalendarSyncPacket;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

/**
 * 客户端入口类（Fabric 26.2 版）。
 * <p>实现 {@link ClientModInitializer}，在 onInitializeClient 中完成：
 * <ol>
 *   <li>加载客户端配置（HUD 位置、样式选择等）</li>
 *   <li>注册按键绑定（C 键打开日历、H 键切换 HUD）</li>
 *   <li>注册客户端网络接收器（接收服务端 CalendarSyncPacket 同步包）</li>
 *   <li>注册 HUD 渲染回调（HudRenderSubscriber）</li>
 *   <li>注册客户端 tick 事件（检测按键按下）</li>
 * </ol>
 *
 * <p>26.2 重构要点：
 * <ul>
 *   <li>{@code KeyBindingHelper} 包名：{@code keybinding.v1} → {@code keymapping.v1}</li>
 *   <li>{@link KeyMapping} 构造第 4 个参数：String → {@link KeyMapping.Category}（record）</li>
 *   <li>自定义分类通过 {@link KeyMapping.Category#register(Identifier)} 注册，
 *       语言键格式为 {@code key.category.<namespace>.<path>}</li>
 *   <li>网络接收器：{@code ClientPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) -> ...)}，
 *       context 提供 {@code client()/player()/responseSender()}</li>
 *   <li>{@link KeyMapping#consumeClick()} 等价旧 wasPressed 语义（一次性消耗点击）</li>
 *   <li>{@code Minecraft.setScreen} → {@code Minecraft.gui.setScreen}（26.2 GUI 重构）</li>
 * </ul>
 */
public class CalendarModClient implements ClientModInitializer {

    /** 自定义按键分类：calendarmod:general，对应语言键 key.category.calendarmod.general */
    public static final KeyMapping.Category CATEGORY_GENERAL = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(CalendarMod.MOD_ID, "general"));

    /** 打开日历界面：默认 C 键 */
    public static KeyMapping KEY_OPEN_CALENDAR;
    /** 切换日历 HUD 显示：默认 H 键 */
    public static KeyMapping KEY_TOGGLE_HUD;

    @Override
    public void onInitializeClient() {
        // 1. 加载客户端配置（HUD 位置、样式选择等）
        CalendarClientConfig.load();

        // 2. 注册按键绑定（Fabric 使用 KeyMappingHelper，等价 Forge 的 RegisterKeyMappingsEvent）
        // 26.2：KeyMapping 构造第 4 个参数从 String 改为 KeyMapping.Category
        // 26.2：方法名 registerKeyBinding → registerKeyMapping
        KEY_OPEN_CALENDAR = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.calendarmod.open_calendar",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_C,
                CATEGORY_GENERAL
        ));
        KEY_TOGGLE_HUD = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.calendarmod.toggle_hud",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                CATEGORY_GENERAL
        ));

        // 3. 注册客户端网络接收器：接收服务端->客户端同步包，更新客户端缓存
        // 26.2：handler 在主线程执行，无需手动 client.execute()
        ClientPlayNetworking.registerGlobalReceiver(CalendarSyncPacket.TYPE, (payload, context) -> {
            CalendarClientCache.INSTANCE.update(
                    payload.getConfig(), payload.getDayZero(),
                    payload.getYearOffset(), payload.getEvents(),
                    payload.getTotalElapsedTicks()
            );
        });

        // 4. 注册 HUD 渲染回调 + 拖拽检测
        HudRenderSubscriber.register();

        // 5. 客户端 tick：检测按键按下（等价 Forge 的 InputEvent.Key）
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (KEY_OPEN_CALENDAR.consumeClick()) {
                CalendarScreen screen = new CalendarScreen();
                screen.setClickHandler(screen::handleClick);
                // 26.2：Minecraft.setScreen → Minecraft.gui.setScreen（GUI 重构）
                Minecraft.getInstance().gui.setScreen(screen);
            }
            while (KEY_TOGGLE_HUD.consumeClick()) {
                CalendarHud.INSTANCE.toggle();
            }
        });

        CalendarMod.LOGGER.info("CalendarMod 客户端初始化完成 (Fabric 26.2)");
    }
}
