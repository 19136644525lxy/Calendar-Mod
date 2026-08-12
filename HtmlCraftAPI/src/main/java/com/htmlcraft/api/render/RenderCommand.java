package com.htmlcraft.api.render;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 渲染指令接口。
 * <p>所有具体渲染指令（填充矩形、绘制文本、绘制纹理等）均实现此接口，
 * 由 {@link RenderPipeline} 统一收集并顺序执行。
 * <p>采用命令模式，将渲染逻辑的"构建"与"执行"解耦，
 * 便于后续扩展（如指令排序、合并、缓存）。
 */
public interface RenderCommand {

    /**
     * 执行单条渲染指令。
     *
     * @param graphics Minecraft 图形上下文，提供 fill/drawString/blit 等原语
     * @param context  渲染上下文，持有字体、全局偏移量等状态
     */
    void execute(GuiGraphics graphics, RenderContext context);
}
