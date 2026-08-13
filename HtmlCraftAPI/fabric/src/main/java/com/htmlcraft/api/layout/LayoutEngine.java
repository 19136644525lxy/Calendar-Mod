package com.htmlcraft.api.layout;

import com.htmlcraft.api.core.HtmlElement;
import com.htmlcraft.api.core.HtmlNode;
import com.htmlcraft.api.core.HtmlText;
import com.htmlcraft.api.style.ComputedStyle;
import com.htmlcraft.api.style.ComputedStyle.AlignItems;
import com.htmlcraft.api.style.ComputedStyle.DisplayType;
import com.htmlcraft.api.style.ComputedStyle.FlexDirection;
import com.htmlcraft.api.style.ComputedStyle.JustifyContent;
import com.htmlcraft.api.style.ComputedStyle.PositionType;

import java.util.ArrayList;
import java.util.List;

/**
 * 布局引擎，递归计算 DOM 树中每个元素的位置和尺寸。
 * <p>支持 Block 布局（块级垂直堆叠 + 行内水平流）和 Flex 布局（ROW / COLUMN）。
 * <p>同时处理 HtmlText 文本节点，为其创建虚拟 LayoutNode 以支持精确布局。
 */
public class LayoutEngine {

    private LayoutEngine() {
    }

    /**
     * 执行布局计算。
     */
    public static LayoutNode layout(HtmlElement root, int contentWidth, int contentHeight) {
        LayoutNode rootNode = new LayoutNode(root);
        rootNode.setWidth(contentWidth);
        rootNode.setHeight(contentHeight);
        layoutChildren(rootNode, contentWidth, contentHeight);
        return rootNode;
    }

    /**
     * 递归布局子元素。
     * 1. 收集文本节点并布局到父容器内容区
     * 2. 收集元素节点并分发到 Block/Flex/Grid 布局
     * 3. 布局 absolute 定位元素
     */
    private static void layoutChildren(LayoutNode parent, int availableWidth, int availableHeight) {
        HtmlElement element = parent.getElement();
        ComputedStyle style = getStyle(element);
        if (style.getDisplay() == DisplayType.NONE) return;

        int[] padding = style.getPadding();
        int contentX = padding[3];
        int contentY = padding[0];
        int contentW = Math.max(0, availableWidth - padding[1] - padding[3]);
        int contentH = Math.max(0, availableHeight - padding[0] - padding[2]);

        // 收集文本节点和元素节点
        List<HtmlText> textNodes = new ArrayList<>();
        List<HtmlElement> flowElements = new ArrayList<>();
        List<HtmlElement> absChildren = new ArrayList<>();

        for (HtmlNode node : element.children()) {
            if (node.isText()) {
                String t = ((HtmlText) node).text();
                if (t != null && !t.trim().isEmpty()) {
                    textNodes.add((HtmlText) node);
                }
            } else if (node.isElement()) {
                HtmlElement c = (HtmlElement) node;
                ComputedStyle cs = getStyle(c);
                if (cs.getDisplay() == DisplayType.NONE) continue;
                PositionType pt = cs.getPosition();
                if (pt == PositionType.ABSOLUTE || pt == PositionType.FIXED) {
                    absChildren.add(c);
                } else {
                    flowElements.add(c);
                }
            }
        }

        // --- 布局文本节点（垂直居中排列） ---
        if (!textNodes.isEmpty()) {
            layoutTextNodes(parent, style, contentX, contentY, contentW, textNodes);
        }

        // 计算文本占用的底部位置
        int textBottom = contentY;
        for (LayoutNode child : parent.getChildren()) {
            if (child.isTextNode()) {
                textBottom = Math.max(textBottom, child.getY() + child.getHeight());
            }
        }

        // --- 布局元素节点（在文本之后） ---
        int elementStartY = textBottom;
        if (textBottom > contentY) {
            elementStartY = textBottom + 4;
        }
        int remainingH = Math.max(0, contentY + contentH - elementStartY);

        if (!flowElements.isEmpty()) {
            if (style.getDisplay() == DisplayType.FLEX) {
                layoutFlex(parent, style, contentX, elementStartY, contentW, remainingH, flowElements);
            } else if (style.getDisplay() == DisplayType.GRID && style.getGridColumns() > 0) {
                layoutGrid(parent, style, contentX, elementStartY, contentW, remainingH, flowElements);
            } else {
                layoutBlock(parent, style, contentX, elementStartY, contentW, remainingH, flowElements);
            }
        }

        // --- 布局 absolute 元素 ---
        int parentContentW = Math.max(1, parent.getWidth() - padding[1] - padding[3]);
        int parentContentH = Math.max(1, parent.getHeight() - padding[0] - padding[2]);
        for (HtmlElement abs : absChildren) {
            ComputedStyle cs = getStyle(abs);
            int[] margin = cs.getMargin();
            int childW = cs.getWidth() > 0 ? cs.getWidth() : Math.max(30, measureWidth(abs));
            int childH = cs.getHeight() > 0 ? cs.getHeight() : measureHeight(abs, childW);

            int cx = contentX + margin[3];
            int cy = contentY + margin[0];
            int L = cs.getLeft(), R = cs.getRight(), T = cs.getTop(), B = cs.getBottom();
            int UNSET = Integer.MIN_VALUE;

            if (L != UNSET) {
                cx = contentX + L + margin[3];
            } else if (R != UNSET) {
                cx = contentX + parentContentW - childW - margin[1] - R;
            }
            if (T != UNSET) {
                cy = contentY + T + margin[0];
            } else if (B != UNSET) {
                cy = contentY + parentContentH - childH - margin[2] - B;
            }
            LayoutNode childNode = createChildNode(parent, abs, cx, cy, childW, childH);
            layoutChildren(childNode, childW, childH);
        }

        // 更新父节点尺寸
        updateParentSize(parent, padding);
    }

    /**
     * 布局文本节点：垂直居中排列在父容器内容区。
     */
    private static void layoutTextNodes(LayoutNode parent, ComputedStyle style,
                                        int contentX, int contentY, int contentW,
                                        List<HtmlText> textNodes) {
        int fontSize = Math.max(8, style.getFontSize());
        int lineH = Math.max(14, fontSize + 2);
        int totalTextH = textNodes.size() * lineH;

        int availableH = Math.max(1, parent.getHeight() - style.getPadding()[0] - style.getPadding()[2]);
        int startOffset = Math.max(0, (availableH - totalTextH) / 2);
        int curY = contentY + startOffset;

        ComputedStyle.TextAlign align = style.getTextAlign();

        for (HtmlText textNode : textNodes) {
            String text = textNode.text();
            if (text == null || text.isEmpty()) continue;

            int textW = estimateTextWidth(text, fontSize);

            int drawX = contentX;
            if (align == ComputedStyle.TextAlign.CENTER) {
                drawX = contentX + (contentW - textW) / 2;
            } else if (align == ComputedStyle.TextAlign.RIGHT) {
                drawX = contentX + contentW - textW;
            }

            LayoutNode textLayout = new LayoutNode(textNode);
            textLayout.setX(drawX);
            textLayout.setY(curY);
            textLayout.setWidth(textW);
            textLayout.setHeight(lineH);
            parent.addChild(textLayout);

            curY += lineH;
        }
    }

    /** 根据所有子节点的最大边界更新父节点尺寸 */
    private static void updateParentSize(LayoutNode parent, int[] padding) {
        int maxRight = 0;
        int maxBottom = 0;
        for (LayoutNode child : parent.getChildren()) {
            int right = child.getX() + child.getWidth();
            int bottom = child.getY() + child.getHeight();
            maxRight = Math.max(maxRight, right);
            maxBottom = Math.max(maxBottom, bottom);
        }
        int contentW = Math.max(0, maxRight - padding[3]);
        int contentH = Math.max(0, maxBottom - padding[0]);
        parent.setWidth(Math.max(parent.getWidth(), contentW + padding[1] + padding[3]));
        parent.setHeight(Math.max(parent.getHeight(), contentH + padding[0] + padding[2]));
    }

    // ===== Block 布局 =====

    private static void layoutBlock(LayoutNode parent, ComputedStyle style,
                                    int x, int y, int w, int h,
                                    List<HtmlElement> flowChildren) {
        int blockY = y;
        int inlineX = x;
        int lineAscent = 0;
        int maxRight = x;
        int[] padding = style.getPadding();

        for (HtmlElement child : flowChildren) {
            ComputedStyle cs = getStyle(child);
            if (cs.getDisplay() == DisplayType.NONE) continue;
            int[] margin = cs.getMargin();

            if (cs.getDisplay() == DisplayType.INLINE) {
                int childW = cs.getWidth() > 0 ? cs.getWidth() : measureWidth(child);
                int estH = cs.getHeight() > 0 ? cs.getHeight() : measureHeight(child, childW);
                int childX = inlineX + margin[3];
                if (inlineX > x && childX + childW + margin[1] > x + w) {
                    blockY += lineAscent;
                    inlineX = x;
                    lineAscent = 0;
                    childX = inlineX + margin[3];
                }
                int childY = blockY + margin[0];
                LayoutNode childNode = createChildNode(parent, child, childX, childY, childW, estH);
                layoutChildren(childNode, childW, estH);
                int actualH = childNode.getHeight();
                childNode.setHeight(actualH);
                inlineX = childX + childW + margin[1];
                lineAscent = Math.max(lineAscent, actualH + margin[0] + margin[2]);
                maxRight = Math.max(maxRight, inlineX);
            } else {
                if (lineAscent > 0) {
                    blockY += lineAscent;
                    inlineX = x;
                    lineAscent = 0;
                }
                int childX = x + margin[3];
                int childY = blockY + margin[0];
                int childW = cs.getWidth() > 0
                        ? cs.getWidth()
                        : Math.max(0, w - margin[1] - margin[3]);
                int estH = cs.getHeight() > 0
                        ? cs.getHeight()
                        : measureHeight(child, childW);
                LayoutNode childNode = createChildNode(parent, child, childX, childY, childW, estH);
                layoutChildren(childNode, childW, estH);
                int actualH = childNode.getHeight();
                childNode.setHeight(actualH);
                blockY = childY + actualH + margin[2];
                maxRight = Math.max(maxRight, childX + childW + margin[1]);
            }
        }

        if (lineAscent > 0) {
            blockY += lineAscent;
        }

        int totalW = Math.max(w, maxRight - x) + padding[1] + padding[3];
        int totalH = Math.max(0, blockY - y) + padding[0] + padding[2];
        parent.setWidth(totalW);
        parent.setHeight(totalH);
    }

    // ===== Grid 布局 =====

    private static void layoutGrid(LayoutNode parent, ComputedStyle style,
                                    int x, int y, int w, int h,
                                    List<HtmlElement> flowChildren) {
        int cols = style.getGridColumns();
        if (cols <= 0) {
            layoutBlock(parent, style, x, y, w, h, flowChildren);
            return;
        }

        int gap = style.getGap();
        int cellW = Math.max(1, (w - gap * (cols - 1)) / cols);
        int actualRowW = cellW * cols + gap * (cols - 1);

        List<HtmlElement> visible = new ArrayList<>();
        for (HtmlElement child : flowChildren) {
            if (getStyle(child).getDisplay() != DisplayType.NONE) {
                visible.add(child);
            }
        }
        if (visible.isEmpty()) return;

        int maxBottom = y;
        int col = 0;
        int rowY = y;

        for (HtmlElement child : visible) {
            ComputedStyle cs = getStyle(child);
            int[] margin = cs.getMargin();

            int cellX = x + col * (cellW + gap) + margin[3];
            int cellY = rowY + margin[0];
            int childW = cellW - margin[1] - margin[3];
            int estH = cs.getHeight() > 0 ? cs.getHeight() : measureHeight(child, childW);

            LayoutNode childNode = createChildNode(parent, child, cellX, cellY, childW, estH);
            layoutChildren(childNode, childW, estH);
            int actualH = childNode.getHeight();
            childNode.setHeight(actualH);

            maxBottom = Math.max(maxBottom, cellY + actualH + margin[2]);

            col++;
            if (col >= cols) {
                col = 0;
                rowY = maxBottom + gap;
            }
        }

        int[] parentPad = style.getPadding();
        parent.setWidth(actualRowW + parentPad[1] + parentPad[3]);
        parent.setHeight(Math.max(0, maxBottom - y) + parentPad[0] + parentPad[2]);
    }

    // ===== Flex 布局 =====

    private static void layoutFlex(LayoutNode parent, ComputedStyle style,
                                   int x, int y, int w, int h,
                                   List<HtmlElement> flowChildren) {
        List<HtmlElement> visible = new ArrayList<>();
        for (HtmlElement child : flowChildren) {
            if (getStyle(child).getDisplay() != DisplayType.NONE) {
                visible.add(child);
            }
        }
        if (visible.isEmpty()) return;

        FlexDirection dir = style.getFlexDirection();
        int gap = style.getGap();
        boolean isRow = dir == FlexDirection.ROW;
        int count = visible.size();

        LayoutNode[] childNodes = new LayoutNode[count];
        for (int i = 0; i < count; i++) {
            HtmlElement child = visible.get(i);
            ComputedStyle cs = getStyle(child);
            int[] margin = cs.getMargin();

            if (isRow) {
                int cw = cs.getWidth() > 0 ? cs.getWidth() : measureWidth(child);
                int ch = cs.getHeight() > 0 ? cs.getHeight() : measureHeight(child, cw);
                childNodes[i] = new LayoutNode(child);
                childNodes[i].setWidth(cw);
                childNodes[i].setHeight(ch);
                layoutChildren(childNodes[i], cw, ch);
            } else {
                int cw = cs.getWidth() > 0
                        ? cs.getWidth()
                        : Math.max(0, w - margin[1] - margin[3]);
                int ch = cs.getHeight() > 0
                        ? cs.getHeight()
                        : measureHeight(child, cw);
                childNodes[i] = new LayoutNode(child);
                childNodes[i].setWidth(cw);
                childNodes[i].setHeight(ch);
                layoutChildren(childNodes[i], cw, ch);
                ch = childNodes[i].getHeight();
                childNodes[i].setHeight(ch);
            }
        }

        int[] mainSizes = new int[count];
        int[] crossSizes = new int[count];
        int[] leadMain = new int[count];
        int[] trailMain = new int[count];
        int[] leadCross = new int[count];
        int[] trailCross = new int[count];
        int totalMain = 0;

        for (int i = 0; i < count; i++) {
            HtmlElement child = visible.get(i);
            ComputedStyle cs = getStyle(child);
            int[] margin = cs.getMargin();

            if (isRow) {
                mainSizes[i] = childNodes[i].getWidth();
                crossSizes[i] = childNodes[i].getHeight();
                leadMain[i] = margin[3];
                trailMain[i] = margin[1];
                leadCross[i] = margin[0];
                trailCross[i] = margin[2];
            } else {
                crossSizes[i] = childNodes[i].getWidth();
                mainSizes[i] = childNodes[i].getHeight();
                leadMain[i] = margin[0];
                trailMain[i] = margin[2];
                leadCross[i] = margin[3];
                trailCross[i] = margin[1];
            }
            totalMain += mainSizes[i] + leadMain[i] + trailMain[i];
        }
        totalMain += gap * Math.max(0, count - 1);

        int containerMain = isRow ? w : h;
        int containerCross = isRow ? h : w;
        int freeSpace = containerMain - totalMain;

        int[] mainOffsets = computeMainOffsets(
                style.getJustifyContent(), count, mainSizes,
                leadMain, trailMain, gap, freeSpace);

        AlignItems align = style.getAlignItems();

        for (int i = 0; i < count; i++) {
            HtmlElement child = visible.get(i);
            ComputedStyle cs = getStyle(child);

            int crossSize;
            boolean crossAuto = isRow ? cs.getHeight() <= 0 : cs.getWidth() <= 0;
            if (align == AlignItems.STRETCH && crossAuto) {
                crossSize = Math.max(0, containerCross - leadCross[i] - trailCross[i]);
            } else {
                crossSize = crossSizes[i];
            }

            int crossPos = computeCrossPosition(
                    align, crossSize, containerCross, leadCross[i], trailCross[i]);

            int childX, childY, childW, childH;
            if (isRow) {
                childX = x + mainOffsets[i];
                childY = y + crossPos;
                childW = mainSizes[i];
                childH = crossSize;
            } else {
                childX = x + crossPos;
                childY = y + mainOffsets[i];
                childW = crossSize;
                childH = mainSizes[i];
            }

            childNodes[i].setX(childX);
            childNodes[i].setY(childY);
            childNodes[i].setWidth(childW);
            childNodes[i].setHeight(childH);
            parent.addChild(childNodes[i]);
        }

        int[] parentPad = style.getPadding();
        int maxRight = 0;
        int maxBottom = 0;
        for (LayoutNode cn : childNodes) {
            maxRight = Math.max(maxRight, cn.getX() + cn.getWidth());
            maxBottom = Math.max(maxBottom, cn.getY() + cn.getHeight());
        }
        int contentW = isRow ? Math.max(w, maxRight - x) : (maxRight > 0 ? maxRight - x : w);
        int contentH = isRow ? (maxBottom > 0 ? maxBottom - y : h) : Math.max(h, maxBottom - y);
        parent.setWidth(contentW + parentPad[1] + parentPad[3]);
        parent.setHeight(contentH + parentPad[0] + parentPad[2]);
    }

    private static int[] computeMainOffsets(JustifyContent justify, int count,
                                            int[] mainSizes, int[] leadMain,
                                            int[] trailMain, int gap, int freeSpace) {
        int[] offsets = new int[count];
        if (count == 0) return offsets;

        int base = 0;
        for (int i = 0; i < count; i++) {
            offsets[i] = base + leadMain[i];
            base += leadMain[i] + mainSizes[i] + trailMain[i];
            if (i < count - 1) base += gap;
        }

        if (freeSpace <= 0) return offsets;

        switch (justify) {
            case FLEX_START -> { /* 已计算 */ }
            case CENTER -> {
                int shift = freeSpace / 2;
                for (int i = 0; i < count; i++) offsets[i] += shift;
            }
            case FLEX_END -> {
                for (int i = 0; i < count; i++) offsets[i] += freeSpace;
            }
            case SPACE_BETWEEN -> {
                if (count > 1) {
                    int spacing = freeSpace / (count - 1);
                    for (int i = 1; i < count; i++) offsets[i] += spacing * i;
                }
            }
            case SPACE_AROUND -> {
                int spacing = freeSpace / count;
                for (int i = 0; i < count; i++) offsets[i] += spacing / 2 + spacing * i;
            }
        }
        return offsets;
    }

    private static int computeCrossPosition(AlignItems align, int childCross,
                                            int containerCross,
                                            int leadCross, int trailCross) {
        int available = containerCross - leadCross - trailCross;
        return switch (align) {
            case FLEX_START, STRETCH -> leadCross;
            case CENTER -> leadCross + (available - childCross) / 2;
            case FLEX_END -> containerCross - trailCross - childCross;
        };
    }

    // ===== 尺寸测量 =====

    private static int measureWidth(HtmlElement element) {
        ComputedStyle style = getStyle(element);
        int[] padding = style.getPadding();
        int textWidth = estimateTextWidth(element, style.getFontSize());

        int childrenWidth = 0;
        for (HtmlElement child : element.getChildren()) {
            ComputedStyle cs = getStyle(child);
            if (cs.getDisplay() == DisplayType.NONE) continue;
            int[] cm = cs.getMargin();
            int cw = cs.getWidth() > 0 ? cs.getWidth() : measureWidth(child);
            childrenWidth += cw + cm[1] + cm[3];
        }
        return Math.max(textWidth, childrenWidth) + padding[1] + padding[3];
    }

    private static int measureHeight(HtmlElement element, int width) {
        ComputedStyle style = getStyle(element);
        if (style.getHeight() > 0) return style.getHeight();

        int[] padding = style.getPadding();
        int fontSize = style.getFontSize();
        int contentWidth = Math.max(1, width - padding[1] - padding[3]);

        int textHeight = estimateTextHeight(element, fontSize, contentWidth);

        int blockHeight = 0;
        int inlineHeight = 0;
        int inlineRowHeight = 0;
        int inlineRowWidth = 0;

        for (HtmlElement child : element.getChildren()) {
            ComputedStyle cs = getStyle(child);
            if (cs.getDisplay() == DisplayType.NONE) continue;
            int[] cm = cs.getMargin();
            int cw = cs.getWidth() > 0 ? cs.getWidth() : contentWidth;
            int ch = cs.getHeight() > 0 ? cs.getHeight() : measureHeight(child, cw);

            if (cs.getDisplay() == DisplayType.INLINE) {
                int childTotalW = cw + cm[1] + cm[3];
                int childTotalH = ch + cm[0] + cm[2];
                if (inlineRowWidth > 0 && inlineRowWidth + childTotalW > contentWidth) {
                    inlineHeight += inlineRowHeight;
                    inlineRowHeight = 0;
                    inlineRowWidth = 0;
                }
                inlineRowWidth += childTotalW;
                inlineRowHeight = Math.max(inlineRowHeight, childTotalH);
            } else {
                blockHeight += ch + cm[0] + cm[2];
            }
        }
        inlineHeight += inlineRowHeight;

        return Math.max(textHeight, blockHeight + inlineHeight) + padding[0] + padding[2];
    }

    /** 估算文本像素宽度（支持中英文混排） */
    private static int estimateTextWidth(String text, int fontSize) {
        if (text == null || text.isEmpty()) return 0;
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c > 127) {
                width += fontSize;
            } else {
                width += Math.max(1, fontSize / 2);
            }
        }
        return width;
    }

    private static int estimateTextWidth(HtmlElement element, int fontSize) {
        String text = getTextContent(element);
        if (text.isEmpty()) return 0;
        return estimateTextWidth(text, fontSize);
    }

    private static int estimateTextHeight(HtmlElement element, int fontSize, int contentWidth) {
        String text = getTextContent(element);
        if (text.isEmpty()) return 0;
        int charWidth = Math.max(1, fontSize / 2);
        int textPixels = text.length() * charWidth;
        int lines = Math.max(1, (textPixels + contentWidth - 1) / contentWidth);
        int lineH = Math.max(14, fontSize + 2);
        return lines * lineH;
    }

    private static String getTextContent(HtmlElement element) {
        StringBuilder sb = new StringBuilder();
        for (HtmlNode node : element.children()) {
            if (node.isText()) {
                sb.append(((HtmlText) node).text());
            } else if (node.isElement()) {
                sb.append(getTextContent((HtmlElement) node));
            }
        }
        return sb.toString();
    }

    // ===== 工具方法 =====

    private static ComputedStyle getStyle(HtmlElement element) {
        if (element.getComputedStyle() instanceof ComputedStyle cs) return cs;
        return ComputedStyle.DEFAULT;
    }

    private static LayoutNode createChildNode(LayoutNode parent, HtmlElement child,
                                              int x, int y, int w, int h) {
        LayoutNode node = new LayoutNode(child);
        node.setX(x);
        node.setY(y);
        node.setWidth(w);
        node.setHeight(h);
        parent.addChild(node);
        return node;
    }
}
