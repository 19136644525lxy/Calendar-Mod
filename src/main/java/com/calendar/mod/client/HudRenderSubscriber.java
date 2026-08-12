package com.calendar.mod.client;

import com.calendar.mod.CalendarMod;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = CalendarMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class HudRenderSubscriber {

    private static final ResourceLocation HOTBAR_ID = ResourceLocation.withDefaultNamespace("hotbar");

    @SubscribeEvent
    public static void onRenderHud(RenderGuiOverlayEvent.Post event) {
        if (!event.getOverlay().id().equals(HOTBAR_ID)) return;
        updateDragging();
        CalendarHud.INSTANCE.render(
                event.getGuiGraphics(),
                event.getWindow().getGuiScaledWidth(),
                event.getWindow().getGuiScaledHeight()
        );
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) return;
        long window = mc.getWindow().getWindow();
        if (window == 0L) return;
        if (GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_RELEASE) {
            CalendarHud.INSTANCE.stopDrag();
        }
    }

    private static void updateDragging() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) return;
        long window = mc.getWindow().getWindow();
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