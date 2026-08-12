package com.htmlcraft.api.core;

/**
 * DOM 文本节点，承载元素之间的纯文本内容。
 */
public class HtmlText implements HtmlNode {

    private final String text;
    private HtmlElement parent;

    public HtmlText(String text) {
        this.text = text == null ? "" : text;
        this.parent = null;
    }

    @Override
    public HtmlElement parent() {
        return parent;
    }

    @Override
    public boolean isElement() {
        return false;
    }

    @Override
    public boolean isText() {
        return true;
    }

    /** 设置父节点（包级访问，由 HtmlElement.addChild 调用） */
    void setParent(HtmlElement parent) {
        this.parent = parent;
    }

    /** 获取文本内容 */
    public String text() {
        return text;
    }

    @Override
    public String toString() {
        return "HtmlText{" + text + "}";
    }
}
