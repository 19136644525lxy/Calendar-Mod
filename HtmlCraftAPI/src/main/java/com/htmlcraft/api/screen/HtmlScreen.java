package com.htmlcraft.api.screen;

import com.htmlcraft.api.core.HtmlDocument;
import com.htmlcraft.api.core.HtmlElement;
import com.htmlcraft.api.layout.LayoutEngine;
import com.htmlcraft.api.layout.LayoutNode;
import com.htmlcraft.api.parser.HtmlParser;
import com.htmlcraft.api.render.RenderContext;
import com.htmlcraft.api.render.RenderPipeline;
import com.htmlcraft.api.style.ComputedStyle;
import com.htmlcraft.api.style.StyleCalculator;
import com.htmlcraft.api.style.StyleSheet;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * HTML 渲染屏幕抽象基类。
 * <p>基于 Minecraft {@link Screen}，将 HTML/CSS 渲染管线接入游戏 GUI。
 * <p>子类实现 {@link #getHtml()} 提供 HTML 内容，可选实现 {@link #getCss()} 提供样式表。
 *
 * <p>渲染流程（{@link #init()} 中执行）：
 * <ol>
 *   <li>{@link HtmlParser} 解析 HTML 为 DOM</li>
 *   <li>{@link StyleSheet} 加载 CSS 规则</li>
 *   <li>{@link StyleCalculator} 计算每个元素的最终样式</li>
 *   <li>{@link LayoutEngine} 执行布局，生成 LayoutNode 树</li>
 *   <li>{@link RenderPipeline} 构建渲染指令</li>
 * </ol>
 *
 * <p>点击事件：{@link #mouseClicked} 通过命中测试找到被点击的元素，
 * 触发 {@link ClickEvent} 回调。可点击元素为 button、a 标签或带 id 的元素。
 *
 * <p>支持居中布局：通过 {@link #setPreferredSize(int, int)} 设置内容区尺寸，
 * 渲染时自动在屏幕上居中。
 */
public abstract class HtmlScreen extends Screen {

    /** 渲染管线，init 阶段构建 */
    private RenderPipeline pipeline;

    /** 布局根节点，用于命中测试 */
    private LayoutNode rootLayout;

    /** 可点击元素命中目标列表 */
    private final List<HitTarget> hitTargets = new CopyOnWriteArrayList<>();

    /** 点击事件处理器（volatile 保证跨线程可见性） */
    private volatile Consumer<ClickEvent> clickHandler;

    /** 内容区首选宽度（0 = 使用屏幕宽度） */
    private int preferredWidth;

    /** 内容区首选高度（0 = 使用屏幕高度） */
    private int preferredHeight;

    /** 布局计算后的偏移量（用于居中） */
    private int layoutOffsetX;
    private int layoutOffsetY;

    protected HtmlScreen(Component title) {
        super(title);
    }

    /**
     * 设置内容区首选尺寸。
     * 渲染时会在此尺寸基础上居中布局。
     *
     * @param width  首选宽度（0 = 使用屏幕宽度）
     * @param height 首选高度（0 = 使用屏幕高度）
     */
    protected void setPreferredSize(int width, int height) {
        this.preferredWidth = Math.max(0, width);
        this.preferredHeight = Math.max(0, height);
    }

    /** 获取布局偏移 X（用于子类在命中测试时调整坐标） */
    public int getLayoutOffsetX() {
        return layoutOffsetX;
    }

    /** 获取布局偏移 Y */
    public int getLayoutOffsetY() {
        return layoutOffsetY;
    }

    /** 子类提供 HTML 内容 */
    protected abstract String getHtml();

    /** 子类可选提供 CSS，默认返回 null（无样式） */
    protected String getCss() {
        return null;
    }

    /**
     * 设置点击事件处理器。
     * 当用户点击可点击元素时触发回调。
     */
    public void setClickHandler(Consumer<ClickEvent> handler) {
        this.clickHandler = handler;
    }

    @Override
    protected void init() {
        rebuild();
    }

    /**
     * 重建渲染管线。
     * 重新解析 HTML/CSS、计算样式、布局并生成渲染指令。
     * 当 HTML 或数据变更后可调用此方法刷新。
     */
    public void rebuild() {
        // 1. 解析 HTML
        HtmlDocument doc = HtmlParser.parse(getHtml());
        HtmlElement root = doc.getRootElement();
        if (root == null) {
            pipeline = null;
            rootLayout = null;
            hitTargets.clear();
            return;
        }

        // 2. 加载 CSS 并计算样式
        StyleSheet styleSheet = new StyleSheet();
        String css = getCss();
        if (css != null && !css.isEmpty()) {
            styleSheet.loadCss(css);
        }
        StyleCalculator.computeStyles(root, styleSheet);

        // 3. 计算内容区尺寸并居中
        int contentW = Math.max(1, preferredWidth > 0 ? preferredWidth : this.width);
        int contentH = Math.max(1, preferredHeight > 0 ? preferredHeight : this.height);
        if (preferredHeight <= 0) {
            contentH = Math.max(1, this.height);
        }
        layoutOffsetX = Math.max(0, (this.width - contentW) / 2);
        layoutOffsetY = Math.max(0, (this.height - contentH) / 2);

        // 4. 布局
        rootLayout = LayoutEngine.layout(root, contentW, contentH);

        // 5. 即时渲染模式：pipeline 仅用于存储滚动状态 + 执行渲染
        RenderContext context = new RenderContext();
        pipeline = new RenderPipeline(context);
        pipeline.clear();

        // 6. 收集可点击元素（坐标已包含居中偏移）
        hitTargets.clear();
        collectHitTargets(rootLayout, layoutOffsetX, layoutOffsetY);
    }

    /** 获取布局根节点（供外部直接调用 pipeline.render 时使用） */
    public LayoutNode getRootLayout() {
        return rootLayout;
    }

    /** 获取渲染管线（供外部执行滚轮等操作） */
    public RenderPipeline getPipeline() {
        return pipeline;
    }

    /** 递归查找指定点下最深的滚动容器 id（用于鼠标滚轮事件分发） */
    private String findScrollContainerAt(LayoutNode node, int absX, int absY,
                                          int parentX, int parentY) {
        if (node == null) return null;
        HtmlElement el = node.getElement();
        if (el == null) return null;
        ComputedStyle cs = el.getComputedStyle() instanceof ComputedStyle s
                ? s : ComputedStyle.DEFAULT;
        if (cs.getDisplay() == ComputedStyle.DisplayType.NONE) return null;

        int x = parentX + node.getX();
        int y = parentY + node.getY();
        int w = node.getWidth();
        int h = node.getHeight();

        // 命中节点区域：先递归子节点（找最深的匹配容器）
        String childResult = null;
        for (LayoutNode ch : node.getChildren()) {
            String r = findScrollContainerAt(ch, absX, absY, x, y);
            if (r != null) { childResult = r; break; }
        }
        if (childResult != null) return childResult;

        // 本节点是否为滚动容器且命中
        String id = el.getId();
        if (id != null && !id.isEmpty()
                && (cs.getOverflowY() == ComputedStyle.Overflow.SCROLL
                    || cs.getOverflowY() == ComputedStyle.Overflow.AUTO)
                && absX >= x && absX <= x + w && absY >= y && absY <= y + h) {
            return id;
        }
        return null;
    }

    /**
     * 递归收集可点击元素。
     * 可点击元素：button、a 标签，或带 id 属性的元素。
     *
     * @param node     当前布局节点
     * @param parentX  父节点绝对 X
     * @param parentY  父节点绝对 Y
     */
    private void collectHitTargets(LayoutNode node, int parentX, int parentY) {
        if (node == null || node.getElement() == null) return;

        HtmlElement el = node.getElement();
        ComputedStyle style = el.getComputedStyle() instanceof ComputedStyle cs
                ? cs : ComputedStyle.DEFAULT;
        // display:none 不参与命中测试
        if (style.getDisplay() == ComputedStyle.DisplayType.NONE) return;

        int absX = parentX + node.getX();
        int absY = parentY + node.getY();

        String tag = el.tagName();
        if ("button".equals(tag) || "a".equals(tag) || el.getId() != null) {
            hitTargets.add(new HitTarget(el, absX, absY,
                    Math.max(0, node.getWidth()), Math.max(0, node.getHeight())));
        }

        for (LayoutNode child : node.getChildren()) {
            collectHitTargets(child, absX, absY);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 1. 渲染半透明背景
        renderBackground(graphics);
        // 2. 执行 HTML 渲染管线（即时遍历模式：rootLayout 作为参数）
        if (pipeline != null && rootLayout != null) {
            graphics.pose().pushPose();
            graphics.pose().translate(layoutOffsetX, layoutOffsetY, 0);
            pipeline.render(graphics, rootLayout);
            graphics.pose().popPose();
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (pipeline != null && rootLayout != null) {
            // 查找鼠标命中的最深滚动容器
            int px = (int) mouseX - layoutOffsetX;
            int py = (int) mouseY - layoutOffsetY;
            String scrollId = findScrollContainerAt(rootLayout, px, py, 0, 0);
            if (scrollId != null) {
                // delta > 0 = 向上滚，内容相对向上 => scrollY 减小；每步 20px
                pipeline.addScroll(scrollId, (int) (-delta * 20));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 命中测试：从后向前遍历（后绘制的元素在上层）
        int px = (int) mouseX;
        int py = (int) mouseY;
        for (int i = hitTargets.size() - 1; i >= 0; i--) {
            HitTarget target = hitTargets.get(i);
            if (target.contains(px, py)) {
                Consumer<ClickEvent> handler = clickHandler;
                if (handler != null) {
                    handler.accept(new ClickEvent(target.element(), button, px, py));
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * 点击事件。
     * 包含被点击的元素、鼠标按键和坐标。
     */
    public static class ClickEvent {
        private final HtmlElement element;
        private final int button;
        private final int x;
        private final int y;

        public ClickEvent(HtmlElement element, int button, int x, int y) {
            this.element = element;
            this.button = button;
            this.x = x;
            this.y = y;
        }

        /** 被点击的 DOM 元素 */
        public HtmlElement element() {
            return element;
        }

        /** 鼠标按键（0=左键，1=右键，2=中键） */
        public int button() {
            return button;
        }

        /** 点击 X 坐标（屏幕绝对坐标） */
        public int x() {
            return x;
        }

        /** 点击 Y 坐标（屏幕绝对坐标） */
        public int y() {
            return y;
        }
    }

    /**
     * 命中测试目标。
     * 记录可点击元素的绝对边界。
     */
    private record HitTarget(HtmlElement element, int x, int y,
                              int width, int height) {
        /** 检查点是否在目标边界内 */
        boolean contains(int px, int py) {
            return px >= x && px <= x + width && py >= y && py <= y + height;
        }
    }
}
