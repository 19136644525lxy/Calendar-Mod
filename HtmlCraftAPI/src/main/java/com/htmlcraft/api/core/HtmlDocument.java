package com.htmlcraft.api.core;

import java.util.ArrayList;
import java.util.List;

/**
 * DOM 文档，持有根元素并提供常用的查找方法。
 */
public class HtmlDocument {

    private HtmlElement rootElement;
    private String title;

    public HtmlElement getRootElement() {
        return rootElement;
    }

    public void setRootElement(HtmlElement rootElement) {
        this.rootElement = rootElement;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    /** 按标签名查找所有匹配的元素（深度优先） */
    public List<HtmlElement> findByTag(String tag) {
        List<HtmlElement> result = new ArrayList<>();
        if (rootElement == null || tag == null) return result;
        String lowerTag = tag.toLowerCase();
        findByTagRecursive(rootElement, lowerTag, result);
        return result;
    }

    private void findByTagRecursive(HtmlElement el, String tag, List<HtmlElement> result) {
        if (el.tagName().equals(tag)) {
            result.add(el);
        }
        for (HtmlNode child : el.children()) {
            if (child.isElement()) {
                findByTagRecursive((HtmlElement) child, tag, result);
            }
        }
    }

    /** 按 id 查找第一个匹配元素（深度优先） */
    public HtmlElement findById(String id) {
        if (rootElement == null || id == null) return null;
        return findByIdRecursive(rootElement, id);
    }

    private HtmlElement findByIdRecursive(HtmlElement el, String id) {
        String elId = el.getId();
        if (elId != null && elId.equals(id)) {
            return el;
        }
        for (HtmlNode child : el.children()) {
            if (child.isElement()) {
                HtmlElement found = findByIdRecursive((HtmlElement) child, id);
                if (found != null) return found;
            }
        }
        return null;
    }

    /** 按 class 查找所有匹配元素（深度优先） */
    public List<HtmlElement> findByClass(String className) {
        List<HtmlElement> result = new ArrayList<>();
        if (rootElement == null || className == null) return result;
        findByClassRecursive(rootElement, className, result);
        return result;
    }

    private void findByClassRecursive(HtmlElement el, String className, List<HtmlElement> result) {
        if (el.hasClass(className)) {
            result.add(el);
        }
        for (HtmlNode child : el.children()) {
            if (child.isElement()) {
                findByClassRecursive((HtmlElement) child, className, result);
            }
        }
    }
}
