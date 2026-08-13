package com.calendar.mod.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

/**
 * HUD 渲染与拖拽事件订阅（Fabric 版）。
 *
 * 原理：Forge 使用 @SubscribeEvent + RenderGuiOverlayEvent.Post 监听 hotbar overlay 渲染后事件；
 * Fabric 改用 HudRenderCallback（每帧 HUD 渲染时调用），无需过滤 overlay。
 * 拖拽检测通过 ClientTickEvents.END_CLIENT_TICK 监听鼠标释放。
 */
public class HudRenderSubscriber {

    private HudRenderSubscriber() {}

    /** 注册 HUD 渲染回调和客户端 tick 事件（由 CalendarModClient 调用） */
    public static void register() {
        // HUD 渲染回调（每帧调用，等价 Forge 的 RenderGuiOverlayEvent.Post）
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            updateDragging();
            MinecraftClient mc = MinecraftClient.getInstance();
            CalendarHud.INSTANCE.render(
                    drawContext,
                    mc.getWindow().getScaledWidth(),
                    mc.getWindow().getScaledHeight()
            );
        });

        // 客户端 tick：检测鼠标释放以停止拖拽
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            long window = client.getWindow().getHandle();
            if (window == 0L) return;
            if (GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_RELEASE) {
                CalendarHud.INSTANCE.stopDrag();
            }
        });
    }

    /** 每帧检测鼠标按下状态，处理 HUD 拖拽 */
    private static void updateDragging() {
        MinecraftClient mc = MinecraftClient.getInstance();
        long window = mc.getWindow().getHandle();
        if (window == 0L) return;
        if (GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) return;

        double[] xArr = new double[1];
        double[] yArr = new double[1];
        GLFW.glfwGetCursorPos(window, xArr, yArr);
        double guiScale = mc.getWindow().getScaleFactor();
        double mouseX = xArr[0] / guiScale;
        double mouseY = yArr[0] / guiScale;
        int screenW = mc.getWindow().getScaledWidth();
        int screenH = mc.getWindow().getScaledHeight();

        if (!CalendarHud.INSTANCE.isDragging()) {
            if (CalendarHud.INSTANCE.isMouseOver((int) mouseX, (int) mouseY, screenW, screenH)) {
                CalendarHud.INSTANCE.startDrag((int) mouseX, (int) mouseY, screenW, screenH);
            }
        } else {
            CalendarHud.INSTANCE.drag((int) mouseX, (int) mouseY, screenW, screenH);
        }
    }
}
