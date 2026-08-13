package com.calendar.mod.client;

import com.calendar.mod.CalendarMod;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * HUD 渲染与拖拽事件订阅（Fabric 26.2 版）。
 *
 * <p>原理：Forge 使用 @SubscribeEvent + RenderGuiOverlayEvent.Post 监听 hotbar overlay 渲染后事件；
 * 26.2 Fabric 改用 {@link HudElementRegistry#addLast(Identifier, net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement)}
 * 注册自定义 HUD 元素，元素回调为 {@code extractRenderState(GuiGraphicsExtractor, DeltaTracker)}。
 * 拖拽检测通过 ClientTickEvents.END_CLIENT_TICK 监听鼠标释放。
 *
 * <p>26.2 重构要点：
 * <ul>
 *   <li>{@code HudRenderCallback} 已移除，改用 {@link HudElementRegistry}</li>
 *   <li>{@code render(DrawContext, float)} → {@code extractRenderState(GuiGraphicsExtractor, DeltaTracker)}</li>
 *   <li>{@link HudElementRegistry#addLast} 不继承任何渲染条件，HUD 始终渲染</li>
 *   <li>HUD 元素 id：calendarmod:hud</li>
 * </ul>
 */
public class HudRenderSubscriber {

    private HudRenderSubscriber() {}

    /** 注册 HUD 渲染元素和客户端 tick 事件（由 CalendarModClient 调用） */
    public static void register() {
        // HUD 渲染元素（每帧调用，等价 Forge 的 RenderGuiOverlayEvent.Post）
        // 26.2：HudElementRegistry.addLast 不继承任何渲染条件，HUD 始终渲染
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(CalendarMod.MOD_ID, "hud"),
                HudRenderSubscriber::extractHudState
        );

        // 客户端 tick：检测鼠标释放以停止拖拽
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            long window = client.getWindow().handle();
            if (window == 0L) return;
            if (GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_RELEASE) {
                CalendarHud.INSTANCE.stopDrag();
            }
        });
    }

    /** HUD 元素渲染回调：先更新拖拽状态，再渲染 HUD */
    private static void extractHudState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        updateDragging();
        Minecraft mc = Minecraft.getInstance();
        CalendarHud.INSTANCE.render(
                graphics,
                mc.getWindow().getGuiScaledWidth(),
                mc.getWindow().getGuiScaledHeight()
        );
    }

    /** 每帧检测鼠标按下状态，处理 HUD 拖拽 */
    private static void updateDragging() {
        Minecraft mc = Minecraft.getInstance();
        long window = mc.getWindow().handle();
        if (window == 0L) return;
        if (GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) return;

        double[] xArr = new double[1];
        double[] yArr = new double[1];
        GLFW.glfwGetCursorPos(window, xArr, yArr);
        double guiScale = mc.getWindow().getGuiScale();
        double mouseX = xArr[0] / guiScale;
        double mouseY = yArr[0] / guiScale;
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        if (!CalendarHud.INSTANCE.isDragging()) {
            if (CalendarHud.INSTANCE.isMouseOver((int) mouseX, (int) mouseY, screenW, screenH)) {
                CalendarHud.INSTANCE.startDrag((int) mouseX, (int) mouseY, screenW, screenH);
            }
        } else {
            CalendarHud.INSTANCE.drag((int) mouseX, (int) mouseY, screenW, screenH);
        }
    }
}
