package com.htmlcraft.api.core;

/**
 * DOM 节点接口，元素节点和文本节点均实现此接口。
 */
public interface HtmlNode {

    /** 获取父元素，根节点的父节点为 null */
    HtmlElement parent();

    /** 是否为元素节点 */
    boolean isElement();

    /** 是否为文本节点 */
    boolean isText();
}
