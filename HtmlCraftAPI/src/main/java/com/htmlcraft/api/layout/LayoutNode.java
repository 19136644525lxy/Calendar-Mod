package com.htmlcraft.api.layout;

import com.htmlcraft.api.core.HtmlElement;
import com.htmlcraft.api.core.HtmlText;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 布局节点，对应 DOM 树中一个元素或文本经过布局计算后的位置和尺寸。
 * <p>元素节点持有 {@link HtmlElement}，文本节点持有 {@link HtmlText}。
 * <p>坐标 (x, y) 相对于父容器的内容区域原点。
 */
public class LayoutNode {

    /** 对应的 DOM 元素（元素节点时非空） */
    private final HtmlElement element;

    /** 对应的 DOM 文本节点（文本节点时非空） */
    private final HtmlText textNode;

    /** 相对于父容器的坐标 */
    private int x, y;

    /** 计算后的尺寸 */
    private int width, height;

    /** 子布局节点 */
    private final List<LayoutNode> children = new ArrayList<>();

    /** 父布局节点 */
    private LayoutNode parent;

    /** 是否为纯文本节点 */
    public boolean isTextNode() {
        return textNode != null && element == null;
    }

    /** 获取文本内容（仅文本节点有效） */
    public String getTextContent() {
        return textNode != null ? textNode.text() : null;
    }

    public LayoutNode(HtmlElement element) {
        this.element = element;
        this.textNode = null;
    }

    /** 创建文本节点 */
    public LayoutNode(HtmlText textNode) {
        this.element = null;
        this.textNode = textNode;
    }

    /** 获取对应的 DOM 元素 */
    public HtmlElement getElement() {
        return element;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public LayoutNode getParent() {
        return parent;
    }

    /** 添加子布局节点，自动建立父引用 */
    public void addChild(LayoutNode child) {
        if (child == null) return;
        child.parent = this;
        children.add(child);
    }

    /** 获取子节点只读视图 */
    public List<LayoutNode> getChildren() {
        return Collections.unmodifiableList(children);
    }

    /** 获取边界矩形（坐标为相对父容器的局部坐标） */
    public Rectangle getBounds() {
        return new Rectangle(x, y, width, height);
    }

    @Override
    public String toString() {
        if (isTextNode()) {
            return "LayoutNode{text:\"" + textNode.text().substring(0, Math.min(20, textNode.text().length()))
                    + "\" @ (" + x + "," + y + ") " + width + "x" + height + "}";
        }
        return "LayoutNode{" + element.getTagName()
                + " @ (" + x + "," + y + ") " + width + "x" + height + "}";
    }
}
