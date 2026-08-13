package com.calendar.mod.client;

import com.calendar.mod.CalendarMod;
import com.calendar.mod.network.CalendarClientCache;
import com.calendar.mod.network.CalendarSyncPacket;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * 客户端入口类（Fabric 版）。
 * <p>实现 {@link ClientModInitializer}，在 onInitializeClient 中完成：
 * <ol>
 *   <li>加载客户端配置（HUD 位置、样式选择等）</li>
 *   <li>注册按键绑定（C 键打开日历、H 键切换 HUD）</li>
 *   <li>注册客户端网络接收器（接收服务端 CalendarSyncPacket 同步包）</li>
 *   <li>注册 HUD 渲染回调（HudRenderSubscriber）</li>
 *   <li>注册客户端 tick 事件（检测按键按下）</li>
 * </ol>
 *
 * <p>与 Forge 版差异：
 * <ul>
 *   <li>{@code RegisterKeyMappingsEvent} → {@link KeyBindingHelper#registerKeyBinding}</li>
 *   <li>{@code InputEvent.Key + @SubscribeEvent} → {@link ClientTickEvents#END_CLIENT_TICK}</li>
 *   <li>{@code KeyMapping.consumeClick()} → {@link KeyBinding#wasPressed()}</li>
 *   <li>{@code SimpleChannel PLAY_TO_CLIENT consumer} → {@link ClientPlayNetworking#registerGlobalReceiver}</li>
 * </ul>
 */
public class CalendarModClient implements ClientModInitializer {

    /** 打开日历界面：默认 C 键 */
    public static KeyBinding KEY_OPEN_CALENDAR;
    /** 切换日历 HUD 显示：默认 H 键 */
    public static KeyBinding KEY_TOGGLE_HUD;

    @Override
    public void onInitializeClient() {
        // 1. 加载客户端配置（HUD 位置、样式选择等）
        CalendarClientConfig.load();

        // 2. 注册按键绑定（Fabric 使用 KeyBindingHelper，等价 Forge 的 RegisterKeyMappingsEvent）
        KEY_OPEN_CALENDAR = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.calendarmod.open_calendar",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_C,
                "category.calendarmod.general"
        ));
        KEY_TOGGLE_HUD = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.calendarmod.toggle_hud",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                "category.calendarmod.general"
        ));

        // 3. 注册客户端网络接收器：接收服务端→客户端同步包，更新客户端缓存
        ClientPlayNetworking.registerGlobalReceiver(CalendarMod.SYNC_ID, (client, handler, buf, responseSender) -> {
            CalendarSyncPacket packet = CalendarSyncPacket.decode(buf);
            // 在主线程执行，保证线程安全（等价 Forge 的 enqueueWork）
            client.execute(() -> CalendarClientCache.INSTANCE.update(
                    packet.getConfig(), packet.getDayZero(),
                    packet.getYearOffset(), packet.getEvents(),
                    packet.getTotalElapsedTicks()
            ));
        });

        // 4. 注册 HUD 渲染回调 + 拖拽检测
        HudRenderSubscriber.register();

        // 5. 客户端 tick：检测按键按下（等价 Forge 的 InputEvent.Key）
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (KEY_OPEN_CALENDAR.wasPressed()) {
                CalendarScreen screen = new CalendarScreen();
                screen.setClickHandler(screen::handleClick);
                MinecraftClient.getInstance().setScreen(screen);
            }
            while (KEY_TOGGLE_HUD.wasPressed()) {
                CalendarHud.INSTANCE.toggle();
            }
        });

        CalendarMod.LOGGER.info("CalendarMod 客户端初始化完成 (Fabric)");
    }
}
