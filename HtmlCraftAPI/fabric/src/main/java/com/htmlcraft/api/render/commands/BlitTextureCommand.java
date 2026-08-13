package com.htmlcraft.api.render.commands;

import com.htmlcraft.api.render.RenderCommand;
import com.htmlcraft.api.render.RenderContext;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/**
 * 绘制纹理指令（Fabric/Yarn 版）。
 * <p>将整张纹理（u=0, v=0 起）缩放绘制到指定矩形区域，
 * 调用 {@code DrawContext.drawTexture} 的完整重载版本。
 *
 * <p>与 Forge 版差异：
 * <ul>
 *   <li>{@code ResourceLocation} → {@link Identifier}</li>
 *   <li>{@code GuiGraphics.blit} → {@code DrawContext.drawTexture}</li>
 * </ul>
 * 两者参数顺序一致：texture, x, y, width, height, u, v, regionWidth, regionHeight, textureWidth, textureHeight。
 */
public class BlitTextureCommand implements RenderCommand {

    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final Identifier location;

    public BlitTextureCommand(int x, int y, int width, int height, Identifier location) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.location = location;
    }

    @Override
    public void execute(DrawContext graphics, RenderContext context) {
        int ox = context.getOffsetX();
        int oy = context.getOffsetY();
        // 完整 drawTexture: 从 (0,0) 取与目标同尺寸的纹理区域，纹理总尺寸等于区域尺寸
        graphics.drawTexture(location, ox + x, oy + y, width, height,
                0.0F, 0.0F, width, height, width, height);
    }
}
