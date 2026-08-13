package com.htmlcraft.api.render.commands;

import com.htmlcraft.api.render.RenderCommand;
import com.htmlcraft.api.render.RenderContext;
import net.minecraft.client.gui.DrawContext;

/**
 * 填充矩形指令（Fabric/Yarn 版）。
 * <p>对应 {@code DrawContext.fill(x1, y1, x2, y2, color)}，
 * 颜色采用 ARGB 格式（0xAARRGGBB），alpha 为 0 时完全透明。
 *
 * <p>与 Forge 版差异：{@code GuiGraphics} → {@link DrawContext}，fill 签名一致。
 */
public class FillRectCommand implements RenderCommand {

    private final int x;
    private final int y;
    private final int width;
    private final int height;
    /** ARGB 颜色 */
    private final int color;

    public FillRectCommand(int x, int y, int width, int height, int color) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.color = color;
    }

    @Override
    public void execute(DrawContext graphics, RenderContext context) {
        int ox = context.getOffsetX();
        int oy = context.getOffsetY();
        // fill 坐标为左上角到右下角
        graphics.fill(ox + x, oy + y, ox + x + width, oy + y + height, color);
    }
}
