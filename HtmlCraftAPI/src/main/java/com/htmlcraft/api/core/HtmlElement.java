package com.htmlcraft.api.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * DOM 元素节点，包含标签名、属性、子节点和父节点引用。
 * <p>children 使用 {@link CopyOnWriteArrayList}，attributes 使用 {@link ConcurrentHashMap}，
 * 以保证遍历与并发修改的安全性。
 */
public class HtmlElement implements HtmlNode {

    private final String tagName;
    private final Map<String, String> attributes;
    private final List<HtmlNode> children;
    private HtmlElement parent;

    /**
     * 计算后的样式，初始为 null。
     * <p>后续由样式计算器填充具体类型，此处用 Object 占位以解耦。
     */
    private Object computedStyle;

    public HtmlElement(String tagName) {
        this.tagName = tagName == null ? "div" : tagName.toLowerCase();
        this.attributes = new ConcurrentHashMap<>();
        this.children = new CopyOnWriteArrayList<>();
        this.parent = null;
        this.computedStyle = null;
    }

    @Override
    public HtmlElement parent() {
        return parent;
    }

    /** parent() 的 JavaBean 别名 */
    public HtmlElement getParent() {
        return parent;
    }

    @Override
    public boolean isElement() {
        return true;
    }

    @Override
    public boolean isText() {
        return false;
    }

    /** 设置父节点（包级访问，由 HtmlElement.addChild 调用） */
    void setParent(HtmlElement parent) {
        this.parent = parent;
    }

    /** 获取标签名（小写） */
    public String tagName() {
        return tagName;
    }

    /** tagName() 的 JavaBean 别名 */
    public String getTagName() {
        return tagName;
    }

    /** 获取属性值，不存在返回 null */
    public String getAttribute(String name) {
        return attributes.get(name.toLowerCase());
    }

    /** 设置属性（属性名转为小写存储） */
    public void setAttribute(String name, String value) {
        attributes.put(name.toLowerCase(), value);
    }

    /** 是否包含指定属性 */
    public boolean hasAttribute(String name) {
        return attributes.containsKey(name.toLowerCase());
    }

    /** 获取全部属性（只读视图） */
    public Map<String, String> attributes() {
        return attributes;
    }

    /**
     * 添加子节点，自动建立父子引用。
     */
    public void addChild(HtmlNode child) {
        Objects.requireNonNull(child, "child 不能为空");
        if (child instanceof HtmlElement el) {
            el.setParent(this);
        } else if (child instanceof HtmlText text) {
            text.setParent(this);
        }
        children.add(child);
    }

    /** 获取全部子节点 */
    public List<HtmlNode> children() {
        return children;
    }

    /** 仅获取元素类型的子节点 */
    public List<HtmlElement> childElements() {
        List<HtmlElement> result = new ArrayList<>();
        for (HtmlNode child : children) {
            if (child.isElement()) {
                result.add((HtmlElement) child);
            }
        }
        return result;
    }

    /** childElements() 的 JavaBean 别名 */
    public List<HtmlElement> getChildren() {
        return childElements();
    }

    /** 获取 id 属性 */
    public String getId() {
        return getAttribute("id");
    }

    /** 获取 class 属性原始字符串 */
    public String getClassName() {
        return getAttribute("class");
    }

    /** 判断是否包含指定 class */
    public boolean hasClass(String className) {
        String cls = getClassName();
        if (cls == null || cls.isEmpty()) return false;
        for (String c : cls.split("\\s+")) {
            if (c.equals(className)) return true;
        }
        return false;
    }

    /** 添加 class（已存在则不重复添加） */
    public void addClass(String className) {
        String cls = getClassName();
        if (cls == null || cls.isEmpty()) {
            setAttribute("class", className);
        } else if (!hasClass(className)) {
            setAttribute("class", cls + " " + className);
        }
    }

    /**
     * 获取计算样式。
     * <p>初始为 null，由样式计算器填充具体类型。
     */
    public Object getComputedStyle() {
        return computedStyle;
    }

    /** 设置计算样式（由样式计算器调用） */
    public void setComputedStyle(Object computedStyle) {
        this.computedStyle = computedStyle;
    }

    @Override
    public String toString() {
        return "HtmlElement{" + tagName + ", attrs=" + attributes + ", children=" + children.size() + "}";
    }
}
