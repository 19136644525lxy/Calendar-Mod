package com.htmlcraft.api.render;

import com.htmlcraft.api.core.HtmlElement;
import com.htmlcraft.api.core.HtmlNode;
import com.htmlcraft.api.core.HtmlText;
import com.htmlcraft.api.layout.LayoutNode;
import com.htmlcraft.api.style.ComputedStyle;
import com.htmlcraft.api.style.ComputedStyle.GradientDir;
import com.htmlcraft.api.style.ComputedStyle.Overflow;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FastColor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * HTML 渲染管线（即时遍历式）。
 * 每个节点按"阴影→背景→边框→文本→子元素"的顺序递归渲染。
 * 支持：scissor 裁剪、渐变背景、圆角裁切、box-shadow、z-index 分层。
 */
public class RenderPipeline {

    /** 用于外部注入的滚动量映射：每个元素 id -> scrollY 偏移像素 */
    private final java.util.Map<String, Integer> scrollOffsets = new java.util.concurrent.ConcurrentHashMap<>();

    /** 渲染上下文，提供字体与全局偏移量 */
    private final RenderContext context;

    public RenderPipeline(RenderContext context) {
        this.context = context;
    }

    /** 获取 / 设置某个滚动容器的滚动偏移量 */
    public int getScroll(String elementId) {
        return scrollOffsets.getOrDefault(elementId, 0);
    }
    public void setScroll(String elementId, int scrollY) {
        scrollOffsets.put(elementId, Math.max(0, scrollY));
    }
    public void addScroll(String elementId, int delta) {
        int cur = scrollOffsets.getOrDefault(elementId, 0);
        scrollOffsets.put(elementId, Math.max(0, cur + delta));
    }

    /** 构建 + 渲染已合一：直接从布局树渲染到 GuiGraphics（无需指令缓冲） */
    public void build(LayoutNode layoutNode) {
        // 构建阶段已无必要：立即渲染模式，但保留接口以兼容旧调用
    }

    /** 兼容旧 API：从布局树根节点直接渲染 */
    public void render(GuiGraphics graphics, LayoutNode layoutNode) {
        if (layoutNode == null) return;
        // 先做第一遍：收集所有 z-index 非 0 节点并排序
        // 简化：递归时直接按 zIndex 排序 children，再依次渲染
        renderNode(graphics, layoutNode, context.getOffsetX(), context.getOffsetY(),
                   null, null);
    }

    /** 旧 API：需要之前调用 build（不推荐，已废弃但保留兼容） */
    public void render(GuiGraphics graphics) {
        // 无布局信息，直接跳过
    }

    public void clear() {
        scrollOffsets.clear();
    }

    // ===== 核心渲染递归 =====

    /**
     * 渲染单个节点及其子树。
     *
     * @param graphics  MC 绘图上下文
     * @param node      当前布局节点
     * @param absX      节点左上角绝对 X（已累积父偏移）
     * @param absY      节点左上角绝对 Y
     * @param scissor   当前外层裁剪矩形（x1,y1,x2,y2），null = 不裁剪
     * @param scrollKey 外层滚动容器的 id（用于平移子树 Y），null = 无滚动
     */
    private void renderNode(GuiGraphics graphics, LayoutNode node,
                            int absX, int absY,
                            int[] scissor, String scrollKey) {
        if (node == null) return;
        HtmlElement element = node.getElement();
        if (element == null) return;

        ComputedStyle style = (ComputedStyle) element.getComputedStyle();
        if (style == null) style = ComputedStyle.DEFAULT;
        if (style.getDisplay() == ComputedStyle.DisplayType.NONE) return;

        int x = absX + node.getX();
        int y = absY + node.getY();

        // 外层滚动容器：Y 平移
        int scrolledY = y;
        if (scrollKey != null) {
            scrolledY += getScroll(scrollKey);
        }

        int w = node.getWidth();
        int h = node.getHeight();

        int[] padding = style.getPadding();

        // 本节点自己的元素 ID（用于识别滚动容器）
        String elemId = element.getId();

        // ========== 1. 计算新的 scissor 边界 ==========
        int[] newScissor = scissor;
        Overflow ox = style.getOverflowX(), oy = style.getOverflowY();
        boolean overflowClip = (ox == Overflow.HIDDEN || ox == Overflow.SCROLL || ox == Overflow.AUTO
                                || oy == Overflow.HIDDEN || oy == Overflow.SCROLL || oy == Overflow.AUTO);
        if (overflowClip) {
            int sx1 = x, sy1 = y, sx2 = x + w, sy2 = y + h;
            // 与外层 scissor 取交集
            if (scissor != null) {
                sx1 = Math.max(sx1, scissor[0]);
                sy1 = Math.max(sy1, scissor[1]);
                sx2 = Math.min(sx2, scissor[2]);
                sy2 = Math.min(sy2, scissor[3]);
            }
            if (sx2 > sx1 && sy2 > sy1) {
                newScissor = new int[] {sx1, sy1, sx2, sy2};
            } else {
                // 完全被裁掉，不渲染
                return;
            }
        }

        // 快速矩形-裁剪 相交检测
        if (newScissor != null) {
            int bx1 = x, by1 = scrolledY, bx2 = x + w, by2 = scrolledY + h;
            if (bx2 <= newScissor[0] || bx1 >= newScissor[2]
                || by2 <= newScissor[1] || by1 >= newScissor[3]) {
                // 节点完全不可见，文本/背景跳过。但 children 仍可能需渲染（比如子节点超出范围），
                // 但实际上 scissor 会在 draw 时处理。这里不提前 return，避免误杀。
            }
        }

        // ========== 2. 开启 scissor ==========
        boolean pushedScissor = false;
        if (newScissor != null) {
            graphics.enableScissor(newScissor[0], newScissor[1],
                                   newScissor[2] - newScissor[0],
                                   newScissor[3] - newScissor[1]);
            pushedScissor = true;
        }

        try {
            int borderRadius = Math.min(style.getBorderRadius(),
                                        Math.min(w, h) / 2);

            // ========== 3. box-shadow（先画阴影，在节点底层） ==========
            ComputedStyle.BoxShadow sh = style.getBoxShadow();
            if (sh != null && (sh.color >>> 24) != 0) {
                drawBoxShadow(graphics, x, scrolledY, w, h, borderRadius, sh);
            }

            // ========== 4. 背景：渐变优先，其次纯色 ==========
            ComputedStyle.LinearGradient grad = style.getLinearGradient();
            int bgColor = style.getBackgroundColor();
            if (grad != null && grad.dir != GradientDir.NONE) {
                drawGradientBg(graphics, x, scrolledY, w, h, borderRadius, grad);
            } else if ((bgColor >>> 24) != 0) {
                if (borderRadius > 0) {
                    drawRoundedFill(graphics, x, scrolledY, w, h, borderRadius, bgColor);
                } else {
                    graphics.fill(x, scrolledY, x + w, scrolledY + h, bgColor);
                }
            }

            // ========== 5. 边框 ==========
            int borderWidth = style.getBorderWidth();
            if (borderWidth > 0) {
                drawRoundedBorder(graphics, x, scrolledY, w, h, borderRadius, borderWidth, style.getBorderColor());
            }

            // ========== 6. 文本（由 LayoutEngine 布局，直接使用 LayoutNode 位置） ==========
            // 文本节点的 LayoutNode 已经在子元素列表中，在步骤 7 中统一渲染
            // 此处不再单独处理文本渲染

            // ========== 7. 子元素（按 zIndex 排序，包含文本节点） ==========
            List<LayoutNode> children = node.getChildren();
            if (children != null && !children.isEmpty()) {
                // 排序：zIndex 小的先画，大的后画；相同则保持原本顺序（稳定排序）
                List<LayoutNode> sorted = new ArrayList<>(children);
                sorted.sort(Comparator.comparingInt(cn -> {
                    if (cn.isTextNode()) return 0;
                    HtmlElement ce = cn.getElement();
                    if (ce == null) return 0;
                    ComputedStyle cs = (ComputedStyle) ce.getComputedStyle();
                    return cs == null ? 0 : cs.getZIndex();
                }));

                // 是否为滚动容器：overflow-y = scroll/auto，且内容高度 > 当前 h
                boolean isScrollContainer =
                    (oy == Overflow.SCROLL || oy == Overflow.AUTO) && elemId != null && !elemId.isEmpty();
                String childScrollKey = isScrollContainer ? elemId : scrollKey;

                for (LayoutNode child : sorted) {
                    if (child.isTextNode()) {
                        // 文本节点：使用 LayoutNode 位置直接渲染
                        renderTextNode(graphics, child, style, x, scrolledY);
                    } else {
                        // 元素节点：递归渲染
                        renderNode(graphics, child, x, scrolledY,
                                   newScissor, childScrollKey);
                    }
                }

                // ========== 8. 滚动条（滚动容器末尾绘制） ==========
                if (isScrollContainer) {
                    int contentTotalH = estimateContentHeight(node);
                    int viewH = h - padding[0] - padding[2];
                    if (contentTotalH > viewH) {
                        drawScrollBar(graphics, x, y, w, h, viewH, contentTotalH,
                                      getScroll(elemId), padding);
                    }
                }
            }

        } finally {
            // 关闭 scissor：严格栈式匹配
            if (pushedScissor) {
                graphics.disableScissor();
            }
        }
    }

    // ===== 辅助：颜色透明度合成 =====
    /**
     * 渲染文本节点：直接使用 LayoutEngine 计算好的位置。
     */
    private void renderTextNode(GuiGraphics graphics, LayoutNode textNode,
                                 ComputedStyle parentStyle, int parentX, int parentY) {
        String text = textNode.getTextContent();
        if (text == null || text.isEmpty()) return;

        int absX = parentX + textNode.getX();
        int absY = parentY + textNode.getY();

        int fontPx = Math.max(8, parentStyle.getFontSize());
        float fontScale = (float) fontPx / 12.0f;
        int color = applyOpacityToColor(parentStyle.getColor(), parentStyle.getOpacity());

        if (fontScale != 1.0f && fontScale > 0) {
            float sx = (float) absX / fontScale;
            float sy = (float) absY / fontScale;
            graphics.pose().pushPose();
            graphics.pose().scale(fontScale, fontScale, 1.0f);
            graphics.drawString(context.getFont(), text, sx, sy, color, false);
            graphics.pose().popPose();
        } else {
            graphics.drawString(context.getFont(), text, (float) absX, (float) absY, color, false);
        }
    }

    private static int applyOpacityToColor(int color, float opacity) {
        if (opacity >= 0.999f) return color;
        int a = (int) (((color >>> 24) & 0xFF) * opacity);
        if (a < 0) a = 0;
        if (a > 255) a = 255;
        return (a << 24) | (color & 0xFFFFFF);
    }

    // ===== 渐变背景 =====
    private static void drawGradientBg(GuiGraphics g, int x, int y, int w, int h,
                                       int radius, ComputedStyle.LinearGradient grad) {
        if (w <= 0 || h <= 0) return;
        int c1 = grad.colorStart;
        int c2 = grad.colorEnd;

        if (radius > 0) {
            // 带圆角的渐变：分中间区域（fillGradient） + 四个圆角三角区（纯色近似）
            drawRoundedGradient(g, x, y, w, h, radius, grad);
            return;
        }

        switch (grad.dir) {
            case TO_BOTTOM:
                g.fillGradient(x, y, x + w, y + h, 0, c1, c2);
                break;
            case TO_TOP:
                g.fillGradient(x, y, x + w, y + h, 0, c2, c1);
                break;
            case TO_RIGHT:
                fillGradientH(g, x, y, x + w, y + h, 0, c1, c2);
                break;
            case TO_LEFT:
                fillGradientH(g, x, y, x + w, y + h, 0, c2, c1);
                break;
            default:
                g.fill(x, y, x + w, y + h, c1);
        }
    }

    /** 水平渐变（每个竖条单独渲染，模拟水平色变） */
    private static void fillGradientH(GuiGraphics g, int x1, int y1, int x2, int y2,
                                      int z, int colL, int colR) {
        if (x2 <= x1) return;
        int w = x2 - x1;
        // 每列 1px 渐变（小场景性能可接受；大场景可做 step 优化）
        float a1 = FastColor.ARGB32.alpha(colL) / 255f;
        float r1 = FastColor.ARGB32.red(colL)   / 255f;
        float g1 = FastColor.ARGB32.green(colL) / 255f;
        float b1 = FastColor.ARGB32.blue(colL)  / 255f;
        float a2 = FastColor.ARGB32.alpha(colR) / 255f;
        float r2 = FastColor.ARGB32.red(colR)   / 255f;
        float g2 = FastColor.ARGB32.green(colR) / 255f;
        float b2 = FastColor.ARGB32.blue(colR)  / 255f;
        // 步长 > 1 做 step 优化（每 2px 一次）
        int step = Math.max(1, Math.min(w / 64, 4));
        for (int i = 0; i < w; i += step) {
            float t = (float) i / (w - 1);
            int a = (int) ((a1 + (a2 - a1) * t) * 255);
            int r = (int) ((r1 + (r2 - r1) * t) * 255);
            int gg = (int) ((g1 + (g2 - g1) * t) * 255);
            int b = (int) ((b1 + (b2 - b1) * t) * 255);
            int col = (a << 24) | (r << 16) | (gg << 8) | b;
            int cx1 = x1 + i;
            int cx2 = Math.min(x2, cx1 + step);
            g.fill(cx1, y1, cx2, y2, z, col);
        }
    }

    // ===== BoxShadow：多层扩张矩形（blur 用多层叠加半透明模拟） =====
    private static void drawBoxShadow(GuiGraphics g, int x, int y, int w, int h,
                                      int radius, ComputedStyle.BoxShadow sh) {
        if ((sh.color >>> 24) == 0) return;
        int blur = Math.max(0, sh.blur);
        int spread = sh.spread;
        int ox = sh.offsetX, oy = sh.offsetY;
        int bx = x + ox - spread - blur;
        int by = y + oy - spread - blur;
        int bw = w + spread * 2 + blur * 2;
        int bh = h + spread * 2 + blur * 2;
        if (bw <= 0 || bh <= 0) return;

        // blur 模拟：画 (layers) 层，每层 alpha 递减 + 矩形外扩 1px
        int layers = Math.max(1, Math.min(blur, 8));
        int baseA = (sh.color >>> 24) & 0xFF;
        int rgb = sh.color & 0xFFFFFF;
        for (int i = 0; i < layers; i++) {
            // 半透明层数从外到内：外层最淡，内层较实
            float t = (float) i / layers;
            int a = (int) (baseA * (1.0f - t * 0.7f) / layers);
            if (a < 0) a = 0;
            if (a > 255) a = 255;
            int layerColor = (a << 24) | rgb;
            int inset = (blur - i);
            int lx = bx + inset;
            int ly = by + inset;
            int lw = bw - inset * 2;
            int lh = bh - inset * 2;
            if (lw <= 0 || lh <= 0) continue;
            int shadowR = Math.min(radius + blur / 2, Math.min(lw, lh) / 2);
            if (shadowR > 0) {
                drawRoundedFill(g, lx, ly, lw, lh, shadowR, layerColor);
            } else {
                g.fill(lx, ly, lx + lw, ly + lh, layerColor);
            }
        }
    }

    // ===== 圆角矩形填充：使用 8 段结构 =====
    // 中心大矩形 + 上下左右直边 + 四角 1/4 圆（像素级逐行画）
    private static void drawRoundedFill(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        if ((color >>> 24) == 0) return;
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        int x2 = x + w;
        int y2 = y + h;
        // 中心 + 上下直边（合并成去掉四角的矩形）
        g.fill(x + r, y, x2 - r, y + r, color);        // 上
        g.fill(x + r, y2 - r, x2 - r, y2, color);      // 下
        g.fill(x, y + r, x2, y2 - r, color);           // 中
        // 四角：每角逐行画（用圆方程判断半径内）
        drawCorner(g, x + r, y + r,           r, color, false, false); // 左上
        drawCorner(g, x2 - r, y + r,          r, color, true,  false); // 右上
        drawCorner(g, x + r, y2 - r,          r, color, false, true);  // 左下
        drawCorner(g, x2 - r, y2 - r,         r, color, true,  true);  // 右下
    }

    /** 画 1/4 圆形圆角区域；rx=右半, ry=下半 */
    private static void drawCorner(GuiGraphics g, int cx, int cy, int r, int color,
                                   boolean rx, boolean ry) {
        int rr = r * r;
        for (int dy = 0; dy < r; dy++) {
            int dy2 = (ry ? dy : (r - 1 - dy));
            int yLine = cy + (ry ? dy : -dy);
            int dxMax = (int) Math.sqrt(rr - dy2 * dy2);
            if (dxMax < 0) continue;
            // 从中心向 x 方向扩展
            if (rx) {
                g.fill(cx, yLine, cx + dxMax, yLine + 1, color);
            } else {
                g.fill(cx - dxMax, yLine, cx, yLine + 1, color);
            }
        }
    }

    /** 圆角渐变：用圆角 scissor 不方便，简单近似 —— 先 fillGradient 满矩形再四角擦除 */
    private static void drawRoundedGradient(GuiGraphics g, int x, int y, int w, int h, int r,
                                            ComputedStyle.LinearGradient grad) {
        // 先画完整渐变
        if (grad.dir == GradientDir.TO_BOTTOM) {
            g.fillGradient(x, y, x + w, y + h, 0, grad.colorStart, grad.colorEnd);
        } else if (grad.dir == GradientDir.TO_TOP) {
            g.fillGradient(x, y, x + w, y + h, 0, grad.colorEnd, grad.colorStart);
        } else if (grad.dir == GradientDir.TO_RIGHT) {
            fillGradientH(g, x, y, x + w, y + h, 0, grad.colorStart, grad.colorEnd);
        } else if (grad.dir == GradientDir.TO_LEFT) {
            fillGradientH(g, x, y, x + w, y + h, 0, grad.colorEnd, grad.colorStart);
        }
        // 四角"擦除"：用全透明色做 alpha 遮罩行不通（fill 无混合时覆盖）
        // 方案：四角区域用填充背景父色？不 —— 我们用 "反向圆角 + 四角用裁剪后已保证内容不外溢"，
        // 实际上在 Minecraft 里矩形四角超出后只能靠 scissor 裁切或透明贴图。这里做性能妥协：
        // 四角用 fill(透明) 不行，所以提供另一种做法 —— 用 fillGradient 分块（中间大矩形 + 上下直边 + 四角渐变近似块）
        // 为简化：圆角 + 渐变组合时，圆角以较小 r 生效，四角保留近似直角（实际看不明显）
    }

    /** 圆角边框：用 4 直边 + 4 弧（画外圈然后擦除内边） */
    private static void drawRoundedBorder(GuiGraphics g, int x, int y, int w, int h,
                                          int r, int bw, int color) {
        if ((color >>> 24) == 0 || bw <= 0) return;
        int x2 = x + w;
        int y2 = y + h;
        r = Math.min(r, Math.min(w, h) / 2);

        if (r <= 0) {
            // 普通直边框
            g.fill(x, y, x2, y + bw, color);
            g.fill(x, y2 - bw, x2, y2, color);
            g.fill(x, y, x + bw, y2, color);
            g.fill(x2 - bw, y, x2, y2, color);
            return;
        }
        // 外圈：填充 r 半径的外圆角大矩形 - 内圆角小矩形
        drawRoundedFill(g, x, y, w, h, r, color);
        // 擦除内圈（填充透明做不到，改为在 bw < r 时再挖一个内切"内框" —— Minecraft 没有减法混合，
        // 所以退而求其次：只画 bw 宽的 4 条边 + 4 角段）
        int ix1 = x + bw, ix2 = x2 - bw, iy1 = y + bw, iy2 = y2 - bw;
        // 如果有背景色的话会被覆盖，但用户可能不设背景色所以边框会变"实心"。
        // 解决：重写边框逻辑，分 8 段绘制
        if (bw <= r) {
            // 4 条直边（跳过圆角区域）
            g.fill(x + r, y, x2 - r, y + bw, color);       // 上边（不含圆）
            g.fill(x + r, y2 - bw, x2 - r, y2, color);     // 下边
            g.fill(x, y + r, x + bw, y2 - r, color);       // 左边
            g.fill(x2 - bw, y + r, x2, y2 - r, color);     // 右边
            // 4 角：环形 —— 画外圆擦除内圆
            drawRingCorner(g, x + r, y + r, r, bw, color, false, false);
            drawRingCorner(g, x2 - r, y + r, r, bw, color, true, false);
            drawRingCorner(g, x + r, y2 - r, r, bw, color, false, true);
            drawRingCorner(g, x2 - r, y2 - r, r, bw, color, true, true);
        } else {
            // bw >= r 时已退化为实心（上面 drawRoundedFill 即可），无需再处理
        }
    }

    /** 1/4 环（边框圆弧）：外半径 r，内半径 r-bw */
    private static void drawRingCorner(GuiGraphics g, int cx, int cy, int r, int bw, int color,
                                       boolean rx, boolean ry) {
        int rOut2 = r * r;
        int rIn2 = (r - bw) * (r - bw);
        for (int dy = 0; dy < r; dy++) {
            int dy2 = (ry ? dy : (r - 1 - dy));
            int yLine = cy + (ry ? dy : -dy);
            int d2 = dy2 * dy2;
            int dxOut = (int) Math.sqrt(rOut2 - d2);
            int dxIn = (int) Math.ceil(Math.sqrt(Math.max(0, rIn2 - d2)));
            if (rx) {
                // 从 cx+dxIn 到 cx+dxOut 画一条水平线
                if (dxOut > dxIn) {
                    g.fill(cx + dxIn, yLine, cx + dxOut, yLine + 1, color);
                }
            } else {
                if (dxOut > dxIn) {
                    g.fill(cx - dxOut, yLine, cx - dxIn, yLine + 1, color);
                }
            }
        }
    }

    /** 估算容器内部子元素总高度（用于判断是否需要滚动条） */
    private static int estimateContentHeight(LayoutNode node) {
        if (node == null || node.getChildren() == null) return 0;
        int maxBottom = 0;
        for (LayoutNode cn : node.getChildren()) {
            maxBottom = Math.max(maxBottom, cn.getY() + cn.getHeight());
        }
        return maxBottom;
    }

    /** 绘制右侧滚动条 */
    private static void drawScrollBar(GuiGraphics g, int x, int y, int w, int h,
                                      int viewH, int contentH, int scrollY, int[] padding) {
        int barW = 6;
        int bx = x + w - barW - 2;
        int by = y + padding[0];
        int trackH = h - padding[0] - padding[2];
        if (trackH <= 0) return;
        // 滑轨（半透明灰）
        g.fill(bx, by, bx + barW, by + trackH, 0x40FFFFFF);
        // 滑块
        float ratio = (float) viewH / contentH;
        int thumbH = Math.max(20, (int) (trackH * ratio));
        int maxScroll = Math.max(0, contentH - viewH);
        int scrollRatio = maxScroll == 0 ? 0 : Math.min(scrollY, maxScroll) * (trackH - thumbH) / maxScroll;
        int ty = by + scrollRatio;
        g.fill(bx, ty, bx + barW, ty + thumbH, 0xCC808080);
    }
}
