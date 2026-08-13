package com.htmlcraft.api.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * 渲染上下文（Fabric/Yarn 版）。
 * <p>持有渲染过程中所需的全局对象（{@link MinecraftClient}、{@link TextRenderer}）
 * 与全局状态（屏幕内绝对定位偏移量）。
 * <p>所有 {@link RenderCommand} 在执行时通过此上下文获取字体并应用偏移量，
 * 避免每条指令各自缓存引用，降低耦合。
 *
 * <p>与 Forge 版差异：
 * <ul>
 *   <li>{@code Minecraft} → {@code MinecraftClient}</li>
 *   <li>{@code Font} → {@code TextRenderer}</li>
 *   <li>{@code minecraft.font} → {@code client.textRenderer}</li>
 * </ul>
 */
public class RenderContext {

    private final MinecraftClient client;
    private final TextRenderer font;

    /** 全局偏移量（用于屏幕内的绝对定位，例如嵌入到已有 GUI 的指定区域） */
    private int offsetX = 0;
    private int offsetY = 0;

    /** 构造渲染上下文，从当前客户端实例获取默认字体 */
    public RenderContext() {
        this.client = MinecraftClient.getInstance();
        this.font = client.textRenderer;
    }

    public MinecraftClient getMinecraft() {
        return client;
    }

    public TextRenderer getFont() {
        return font;
    }

    public int getOffsetX() {
        return offsetX;
    }

    public int getOffsetY() {
        return offsetY;
    }

    /** 设置全局偏移量，后续指令坐标均会叠加此偏移 */
    public void setOffset(int x, int y) {
        this.offsetX = x;
        this.offsetY = y;
    }
}
