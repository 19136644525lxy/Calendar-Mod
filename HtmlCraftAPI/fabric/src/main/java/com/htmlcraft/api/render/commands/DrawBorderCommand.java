package com.htmlcraft.api.render.commands;

import com.htmlcraft.api.render.RenderCommand;
import com.htmlcraft.api.render.RenderContext;
import net.minecraft.client.gui.DrawContext;

/**
 * 绘制边框指令（Fabric/Yarn 版）。
 * <p>通过 4 次 {@code DrawContext.fill} 绘制上、右、下、左四条边，
 * 边框绘制在矩形内侧，避免溢出到相邻元素。
 * 颜色采用 ARGB 格式（0xAARRGGBB）。
 *
 * <p>与 Forge 版差异：{@code GuiGraphics} → {@link DrawContext}，fill 签名一致。
 */
public class DrawBorderCommand implements RenderCommand {

    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final int borderWidth;
    /** ARGB 颜色 */
    private final int color;

    public DrawBorderCommand(int x, int y, int width, int height, int borderWidth, int color) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.borderWidth = borderWidth;
        this.color = color;
    }

    @Override
    public void execute(DrawContext graphics, RenderContext context) {
        int ox = context.getOffsetX();
        int oy = context.getOffsetY();
        int x1 = ox + x;
        int y1 = oy + y;
        int x2 = ox + x + width;
        int y2 = oy + y + height;
        int bw = borderWidth;

        // 上边
        graphics.fill(x1, y1, x2, y1 + bw, color);
        // 下边
        graphics.fill(x1, y2 - bw, x2, y2, color);
        // 左边
        graphics.fill(x1, y1, x1 + bw, y2, color);
        // 右边
        graphics.fill(x2 - bw, y1, x2, y2, color);
    }
}
