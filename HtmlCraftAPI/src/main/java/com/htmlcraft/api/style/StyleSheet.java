package com.htmlcraft.api.style;

import com.htmlcraft.api.core.HtmlElement;
import com.htmlcraft.api.parser.CssParser;
import com.htmlcraft.api.parser.CssParser.CssRule;
import com.htmlcraft.api.parser.CssParser.Selector;
import com.htmlcraft.api.parser.CssParser.SimpleSelector;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 样式表，包含 CSS 规则列表并提供元素匹配能力。
 * 线程安全：规则列表使用 CopyOnWriteArrayList。
 * 层叠规则：后面的规则覆盖前面的。
 */
public class StyleSheet {

    private final CopyOnWriteArrayList<CssRule> rules = new CopyOnWriteArrayList<>();

    /** 添加单条规则 */
    public void addRule(CssRule rule) {
        if (rule != null) rules.add(rule);
    }

    /** 批量添加规则 */
    public void addRules(List<CssRule> newRules) {
        if (newRules != null) {
            for (CssRule rule : newRules) {
                if (rule != null) rules.add(rule);
            }
        }
    }

    /** 从 CSS 文本批量加载规则 */
    public void loadCss(String css) {
        addRules(CssParser.parse(css));
    }

    /** 获取所有规则（不可变视图） */
    public List<CssRule> getRules() {
        return Collections.unmodifiableList(rules);
    }

    /** 清空所有规则 */
    public void clear() {
        rules.clear();
    }

    /**
     * 匹配元素，返回合并后的属性 Map。
     * 后面的规则覆盖前面的（CSS 层叠规则）。
     */
    public Map<String, String> match(HtmlElement element) {
        Map<String, String> result = new HashMap<>();
        for (CssRule rule : rules) {
            if (matches(rule.getSelector(), element)) {
                Map<String, String> props = rule.getProperties();
                if (props != null) result.putAll(props);
            }
        }
        return result;
    }

    /**
     * 检查 CssParser.Selector 是否匹配指定元素。
     * 支持后代选择器（沿祖先链向上匹配）。
     */
    private boolean matches(Selector selector, HtmlElement element) {
        if (selector == null || element == null) return false;
        List<SimpleSelector> parts = selector.getParts();
        if (parts.isEmpty()) return false;

        // 最后一个简单选择器必须匹配当前元素
        int idx = parts.size() - 1;
        if (!matchesSimple(parts.get(idx), element)) return false;
        idx--;

        // 沿祖先链向上匹配剩余选择器
        HtmlElement ancestor = element.getParent();
        while (idx >= 0 && ancestor != null) {
            if (matchesSimple(parts.get(idx), ancestor)) {
                idx--;
            }
            ancestor = ancestor.getParent();
        }
        return idx < 0;
    }

    /** 检查简单选择器是否匹配单个元素 */
    private boolean matchesSimple(SimpleSelector ss, HtmlElement element) {
        // 通配符直接通过
        if (ss.isUniversal()) return true;
        // 标签匹配
        if (ss.getTag() != null && !ss.getTag().equals(element.getTagName())) return false;
        // ID 匹配
        if (ss.getId() != null && !ss.getId().equals(element.getId())) return false;
        // 类匹配（所有类都必须存在）
        if (ss.getClasses() != null) {
            for (String cls : ss.getClasses()) {
                if (!element.hasClass(cls)) return false;
            }
        }
        return true;
    }
}
