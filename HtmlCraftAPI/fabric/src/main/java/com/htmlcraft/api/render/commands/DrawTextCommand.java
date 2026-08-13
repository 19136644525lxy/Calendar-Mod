package com.htmlcraft.api.render.commands;

import com.htmlcraft.api.render.RenderCommand;
import com.htmlcraft.api.render.RenderContext;
import net.minecraft.client.gui.DrawContext;

/**
 * 绘制文本指令（Fabric/Yarn 版）。
 * <p>对应 {@code DrawContext.drawText(font, text, x, y, color, shadow)}，
 * 颜色采用 ARGB 格式（0xAARRGGBB）。
 *
 * <p>与 Forge 版差异：
 * <ul>
 *   <li>{@code GuiGraphics.drawString} → {@code DrawContext.drawText}</li>
 *   <li>Yarn 中 drawText 的 x, y 参数为 int 类型（Forge 重载有 float 版本）</li>
 * </ul>
 */
public class DrawTextCommand implements RenderCommand {

    private final int x;
    private final int y;
    private final String text;
    /** ARGB 颜色 */
    private final int color;
    private final boolean shadow;

    public DrawTextCommand(int x, int y, String text, int color, boolean shadow) {
        this.x = x;
        this.y = y;
        this.text = text;
        this.color = color;
        this.shadow = shadow;
    }

    @Override
    public void execute(DrawContext graphics, RenderContext context) {
        int ox = context.getOffsetX();
        int oy = context.getOffsetY();
        // Yarn: drawText(TextRenderer, String, int x, int y, int color, boolean shadow)
        graphics.drawText(context.getFont(), text, ox + x, oy + y, color, shadow);
    }
}
