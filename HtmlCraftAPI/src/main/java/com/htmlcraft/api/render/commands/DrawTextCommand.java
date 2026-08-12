package com.htmlcraft.api.render.commands;

import com.htmlcraft.api.render.RenderCommand;
import com.htmlcraft.api.render.RenderContext;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 绘制文本指令。
 * <p>对应 {@code GuiGraphics.drawString(font, text, x, y, color, shadow)}，
 * 颜色采用 ARGB 格式（0xAARRGGBB）。
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
    public void execute(GuiGraphics graphics, RenderContext context) {
        int ox = context.getOffsetX();
        int oy = context.getOffsetY();
        graphics.drawString(context.getFont(), text, ox + x, oy + y, color, shadow);
    }
}
