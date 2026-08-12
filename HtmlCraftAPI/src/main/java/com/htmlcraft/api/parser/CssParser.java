package com.htmlcraft.api.parser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * CSS 解析器，将 CSS 文本解析为 {@link CssRule} 列表。
 * <p>支持选择器类型：标签(div)、类(.class)、ID(#id)、通配(*)、后代(div span)、多类(.a.b)。
 * <p>支持逗号分隔的多个选择器共享同一组属性。
 */
public class CssParser {

    /**
     * 解析 CSS 字符串。
     *
     * @param css CSS 文本，为 null 或空时返回空列表
     * @return 解析后的规则列表
     */
    public static List<CssRule> parse(String css) {
        List<CssRule> rules = new ArrayList<>();
        if (css == null || css.isEmpty()) return rules;

        int length = css.length();
        int pos = 0;

        while (pos < length) {
            // 跳过空白与注释
            pos = skipWhitespaceAndComments(css, pos, length);
            if (pos >= length) break;

            // 定位选择器结束位置
            int bracePos = css.indexOf('{', pos);
            if (bracePos < 0) break;

            String selectorStr = css.substring(pos, bracePos).trim();
            pos = bracePos + 1;

            // 定位属性块结束位置
            int closePos = css.indexOf('}', pos);
            if (closePos < 0) closePos = length;

            String propsStr = css.substring(pos, closePos);
            pos = closePos + 1;

            if (selectorStr.isEmpty()) continue;

            // 解析选择器（逗号分隔的多个）
            List<Selector> selectors = parseSelectors(selectorStr);
            Map<String, String> properties = parseProperties(propsStr);

            for (Selector selector : selectors) {
                rules.add(new CssRule(selector, properties));
            }
        }
        return rules;
    }

    /** 解析逗号分隔的多个选择器 */
    private static List<Selector> parseSelectors(String selectorStr) {
        List<Selector> selectors = new ArrayList<>();
        for (String part : selectorStr.split(",")) {
            part = part.trim();
            if (part.isEmpty()) continue;
            Selector sel = parseSelector(part);
            if (sel != null) selectors.add(sel);
        }
        return selectors;
    }

    /** 解析单个选择器（按空格拆分为后代组合） */
    private static Selector parseSelector(String selectorStr) {
        List<SimpleSelector> parts = new ArrayList<>();
        String[] tokens = selectorStr.split("\\s+");
        for (String token : tokens) {
            if (token.isEmpty()) continue;
            SimpleSelector ss = parseSimpleSelector(token);
            if (ss != null) parts.add(ss);
        }
        if (parts.isEmpty()) return null;
        return new Selector(parts);
    }

    /**
     * 解析简单选择器，支持: div / .class / #id / * / div.a#id / .a.b
     */
    private static SimpleSelector parseSimpleSelector(String token) {
        String tag = null;
        String id = null;
        List<String> classes = new ArrayList<>();
        boolean universal = false;

        int i = 0;
        int len = token.length();

        // 标签名或通配符（以字母或 * 开头）
        if (i < len) {
            char c = token.charAt(i);
            if (c == '*') {
                universal = true;
                i++;
            } else if (Character.isLetter(c)) {
                int start = i;
                while (i < len) {
                    char ch = token.charAt(i);
                    if (Character.isLetterOrDigit(ch) || ch == '-' || ch == '_') {
                        i++;
                    } else {
                        break;
                    }
                }
                tag = token.substring(start, i).toLowerCase(Locale.ROOT);
            }
        }

        // 解析 .class 和 #id
        while (i < len) {
            char c = token.charAt(i);
            if (c == '.') {
                i++;
                int start = i;
                while (i < len) {
                    char ch = token.charAt(i);
                    if (Character.isLetterOrDigit(ch) || ch == '-' || ch == '_') {
                        i++;
                    } else {
                        break;
                    }
                }
                if (i > start) classes.add(token.substring(start, i));
            } else if (c == '#') {
                i++;
                int start = i;
                while (i < len) {
                    char ch = token.charAt(i);
                    if (Character.isLetterOrDigit(ch) || ch == '-' || ch == '_') {
                        i++;
                    } else {
                        break;
                    }
                }
                if (i > start) id = token.substring(start, i);
            } else {
                i++; // 跳过未知字符
            }
        }

        return new SimpleSelector(tag, id, classes, universal);
    }

    /** 解析属性声明块（智能识别括号内分号，避免错误分割） */
    private static Map<String, String> parseProperties(String propsStr) {
        Map<String, String> properties = new LinkedHashMap<>();
        int depth = 0; // 括号深度
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < propsStr.length(); i++) {
            char c = propsStr.charAt(i);

            if (c == '(') {
                depth++;
                current.append(c);
            } else if (c == ')') {
                depth = Math.max(0, depth - 1);
                current.append(c);
            } else if (c == ';' && depth == 0) {
                // 只在括号外的分号处分割
                addProperty(properties, current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }

        // 处理最后一个属性（没有分号结尾）
        if (current.length() > 0) {
            addProperty(properties, current.toString());
        }

        return properties;
    }

    /** 将单条声明添加到属性表 */
    private static void addProperty(Map<String, String> properties, String decl) {
        decl = decl.trim();
        if (decl.isEmpty()) return;
        int colon = decl.indexOf(':');
        if (colon < 0) return;
        String name = decl.substring(0, colon).trim().toLowerCase(Locale.ROOT);
        String value = decl.substring(colon + 1).trim();
        if (!name.isEmpty() && !value.isEmpty()) {
            properties.put(name, value);
        }
    }

    /** 跳过空白字符与 CSS 块注释 */
    private static int skipWhitespaceAndComments(String css, int pos, int length) {
        while (pos < length) {
            // 跳过空白
            while (pos < length && Character.isWhitespace(css.charAt(pos))) {
                pos++;
            }
            // 跳过块注释
            if (pos + 1 < length && css.charAt(pos) == '/' && css.charAt(pos + 1) == '*') {
                pos += 2;
                while (pos + 1 < length && !(css.charAt(pos) == '*' && css.charAt(pos + 1) == '/')) {
                    pos++;
                }
                pos += 2; // 跳过 */
            } else {
                break;
            }
        }
        return pos;
    }

    /**
     * CSS 规则：选择器 + 属性映射。
     */
    public static class CssRule {
        private final Selector selector;
        private final Map<String, String> properties;

        public CssRule(Selector selector, Map<String, String> properties) {
            this.selector = selector;
            this.properties = properties;
        }

        public Selector getSelector() {
            return selector;
        }

        public Map<String, String> getProperties() {
            return properties;
        }

        @Override
        public String toString() {
            return "CssRule{" + selector + " -> " + properties + "}";
        }
    }

    /**
     * 选择器，由多个 {@link SimpleSelector} 组成后代链。
     * <p>如 "div .a span" 解析为三个简单选择器。
     */
    public static class Selector {
        private final List<SimpleSelector> parts;

        public Selector(List<SimpleSelector> parts) {
            this.parts = parts;
        }

        public List<SimpleSelector> getParts() {
            return parts;
        }

        @Override
        public String toString() {
            return String.join(" ", parts.stream().map(Object::toString).toList());
        }
    }

    /**
     * 简单选择器：标签 + ID + 类的组合。
     * <p>如 div.a#b 表示标签 div、类 a、ID b。
     */
    public static class SimpleSelector {
        private final String tag;
        private final String id;
        private final List<String> classes;
        private final boolean universal;

        public SimpleSelector(String tag, String id, List<String> classes, boolean universal) {
            this.tag = tag;
            this.id = id;
            this.classes = classes;
            this.universal = universal;
        }

        public String getTag() {
            return tag;
        }

        public String getId() {
            return id;
        }

        public List<String> getClasses() {
            return classes;
        }

        public boolean isUniversal() {
            return universal;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            if (universal) {
                sb.append('*');
            } else if (tag != null) {
                sb.append(tag);
            }
            if (id != null) sb.append('#').append(id);
            for (String c : classes) sb.append('.').append(c);
            return sb.toString();
        }
    }
}
