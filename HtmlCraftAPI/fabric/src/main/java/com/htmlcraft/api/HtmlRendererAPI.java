package com.htmlcraft.api;

import com.htmlcraft.api.binding.DataContext;
import com.htmlcraft.api.binding.TemplateEngine;
import com.htmlcraft.api.screen.HtmlScreen;
import net.minecraft.text.Text;

import java.util.function.Consumer;

/**
 * HtmlCraft API 对外统一入口（Fabric/Yarn 版）。
 * 提供便捷的 HTML 渲染 Screen 创建方法。
 *
 * <p>与 Forge 版差异：仅将 {@code Component} 替换为 {@link Text}。
 */
public class HtmlRendererAPI {

    /**
     * 创建一个 HTML Screen 构建器。
     * 链式调用设置 HTML、CSS、数据、事件等。
     */
    public static Builder createScreen(Text title) {
        return new Builder(title);
    }

    /**
     * Screen 构建器。
     * 通过链式调用配置 HTML 内容、CSS 样式、数据上下文与点击回调，
     * 最终 {@link #build()} 生成可被 {@code MinecraftClient.getInstance().setScreen()} 打开的 {@link HtmlScreen}。
     */
    public static class Builder {
        private final Text title;
        private String html;
        private String css;
        private DataContext dataContext;
        private Consumer<HtmlScreen.ClickEvent> clickHandler;

        Builder(Text title) {
            this.title = title;
        }

        public Builder html(String html) {
            this.html = html;
            return this;
        }

        public Builder css(String css) {
            this.css = css;
            return this;
        }

        public Builder data(DataContext context) {
            this.dataContext = context;
            return this;
        }

        public Builder onClick(Consumer<HtmlScreen.ClickEvent> handler) {
            this.clickHandler = handler;
            return this;
        }

        /**
         * 构建 HtmlScreen 实例。
         * <p>返回的 Screen 应通过 {@code MinecraftClient.getInstance().setScreen(screen)} 打开。
         * <p>每次 {@link HtmlScreen#init()} 会重新调用 {@link #getHtml()}，
         * 因此模板中的 {{variable}} 会在屏幕初始化时根据当前 DataContext 渲染。
         *
         * @return 配置完成的 HtmlScreen
         */
        public HtmlScreen build() {
            final String finalHtml = html != null ? html : "<div>无内容</div>";
            final String finalCss = css;
            final DataContext finalData = dataContext != null ? dataContext : new DataContext();
            final Consumer<HtmlScreen.ClickEvent> finalHandler = clickHandler;

            HtmlScreen screen = new HtmlScreen(title) {
                @Override
                protected String getHtml() {
                    return TemplateEngine.render(finalHtml, finalData);
                }

                @Override
                protected String getCss() {
                    return finalCss;
                }
            };
            // 正确设置点击事件处理器
            if (finalHandler != null) {
                screen.setClickHandler(finalHandler);
            }
            return screen;
        }
    }
}
