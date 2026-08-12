package com.htmlcraft.api.parser;

import com.htmlcraft.api.core.HtmlDocument;
import com.htmlcraft.api.core.HtmlElement;
import com.htmlcraft.api.core.HtmlNode;
import com.htmlcraft.api.core.HtmlText;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 轻量 HTML 解析器，基于递归下降法实现。
 * <p>支持常见标签（div/span/p/button/img/h1-h6/br/hr/a/ul/li/table/tr/td/th/input/label/mc-item），
 * 支持自闭合标签、属性解析、文本节点与嵌套结构。
 * <p>不支持 script/style 标签内容（会跳过），CSS 需单独传入 {@link CssParser}。
 */
public class HtmlParser {

    /** 自闭合（void）标签集合，此类标签不解析子节点 */
    private static final Set<String> VOID_TAGS = Set.of(
            "br", "hr", "img", "input"
    );

    private final String html;
    private final int length;
    private int pos;

    private HtmlParser(String html) {
        this.html = html;
        this.length = html.length();
        this.pos = 0;
    }

    /**
     * 解析 HTML 字符串为 {@link HtmlDocument}。
     *
     * @param html HTML 文本，为 null 或空时返回包含空 div 根的文档
     * @return 解析后的文档
     */
    public static HtmlDocument parse(String html) {
        if (html == null || html.isEmpty()) {
            HtmlDocument doc = new HtmlDocument();
            doc.setRootElement(new HtmlElement("div"));
            return doc;
        }
        HtmlParser parser = new HtmlParser(html);
        return parser.parseDocument();
    }

    /** 解析整个文档 */
    private HtmlDocument parseDocument() {
        HtmlDocument doc = new HtmlDocument();
        List<HtmlNode> topNodes = new ArrayList<>();

        while (pos < length) {
            skipWhitespace();
            if (pos >= length) break;

            // 跳过 DOCTYPE 声明
            if (startsWithIgnoreCase("<!doctype")) {
                skipUntilChar('>');
                if (pos < length) pos++;
                continue;
            }
            // 跳过注释
            if (startsWith("<!--")) {
                skipComment();
                continue;
            }

            if (peek() == '<') {
                // 孤立的结束标签，直接跳过
                if (pos + 1 < length && html.charAt(pos + 1) == '/') {
                    skipUntilChar('>');
                    if (pos < length) pos++;
                    continue;
                }
                // 跳过 script/style（不支持内容）
                if (startsWithIgnoreCase("<script") || startsWithIgnoreCase("<style")) {
                    skipUnsupportedTag();
                    continue;
                }
                HtmlElement el = parseElement();
                if (el != null) {
                    topNodes.add(el);
                }
            } else {
                // 顶层文本节点
                String text = parseText();
                if (!text.trim().isEmpty()) {
                    topNodes.add(new HtmlText(text));
                }
            }
        }

        doc.setRootElement(buildRoot(topNodes));
        // 尝试提取 title
        HtmlElement titleEl = findFirstByTag(doc.getRootElement(), "title");
        if (titleEl != null) {
            StringBuilder sb = new StringBuilder();
            for (HtmlNode child : titleEl.children()) {
                if (child.isText()) {
                    sb.append(((HtmlText) child).text());
                }
            }
            doc.setTitle(sb.toString().trim());
        }
        return doc;
    }

    /** 根据顶层节点构建根元素：单元素直接返回，多节点用 div 包装 */
    private HtmlElement buildRoot(List<HtmlNode> topNodes) {
        if (topNodes.isEmpty()) {
            return new HtmlElement("div");
        }
        if (topNodes.size() == 1 && topNodes.get(0).isElement()) {
            return (HtmlElement) topNodes.get(0);
        }
        HtmlElement wrapper = new HtmlElement("div");
        for (HtmlNode node : topNodes) {
            wrapper.addChild(node);
        }
        return wrapper;
    }

    /** 解析一个元素：标签名 -> 属性 -> 子节点 */
    private HtmlElement parseElement() {
        pos++; // 跳过 '<'
        String tagName = parseTagName();
        if (tagName.isEmpty()) return null;
        tagName = tagName.toLowerCase(Locale.ROOT);

        HtmlElement element = new HtmlElement(tagName);
        parseAttributes(element);
        skipWhitespace();
        // 处理自闭合斜杠
        if (pos < length && peek() == '/') pos++;
        if (pos < length && peek() == '>') pos++;

        // void 标签无子节点
        if (VOID_TAGS.contains(tagName)) {
            return element;
        }
        parseChildren(element, tagName);
        return element;
    }

    /** 解析子节点，直到遇到匹配的结束标签或文档末尾 */
    private void parseChildren(HtmlElement parent, String tagName) {
        while (pos < length) {
            if (startsWith("<!--")) {
                skipComment();
                continue;
            }
            if (peek() == '<') {
                // 结束标签
                if (pos + 1 < length && html.charAt(pos + 1) == '/') {
                    int savePos = pos;
                    pos += 2; // 跳过 </
                    String closeTag = parseTagName().toLowerCase(Locale.ROOT);
                    skipWhitespace();
                    if (pos < length && peek() == '>') pos++;

                    if (closeTag.equals(tagName)) {
                        return; // 匹配，正常结束
                    }
                    // 不匹配，回退交给上层处理
                    pos = savePos;
                    return;
                }
                // 跳过 script/style
                if (startsWithIgnoreCase("<script") || startsWithIgnoreCase("<style")) {
                    skipUnsupportedTag();
                    continue;
                }
                // 递归解析子元素
                HtmlElement child = parseElement();
                if (child != null) {
                    parent.addChild(child);
                }
            } else {
                String text = parseText();
                if (!text.isEmpty()) {
                    parent.addChild(new HtmlText(text));
                }
            }
        }
    }

    /** 解析标签名（支持字母、数字、-、_，兼容 mc-item） */
    private String parseTagName() {
        int start = pos;
        while (pos < length) {
            char c = html.charAt(pos);
            if (Character.isLetterOrDigit(c) || c == '-' || c == '_') {
                pos++;
            } else {
                break;
            }
        }
        return html.substring(start, pos);
    }

    /** 解析属性列表 */
    private void parseAttributes(HtmlElement element) {
        while (pos < length) {
            skipWhitespace();
            if (pos >= length) break;
            char c = peek();
            if (c == '>' || c == '/') break;

            String name = parseAttributeName();
            if (name.isEmpty()) {
                pos++; // 避免死循环
                continue;
            }
            skipWhitespace();
            String value = "";
            if (pos < length && peek() == '=') {
                pos++;
                skipWhitespace();
                value = parseAttributeValue();
            }
            element.setAttribute(name, value);
        }
    }

    /** 解析属性名（支持 data-* 等，含冒号） */
    private String parseAttributeName() {
        int start = pos;
        while (pos < length) {
            char c = html.charAt(pos);
            if (Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == ':') {
                pos++;
            } else {
                break;
            }
        }
        return html.substring(start, pos);
    }

    /** 解析属性值，支持双引号、单引号和无引号三种形式 */
    private String parseAttributeValue() {
        if (pos >= length) return "";
        char quote = peek();
        if (quote == '"' || quote == '\'') {
            pos++;
            int start = pos;
            while (pos < length && peek() != quote) pos++;
            String value = html.substring(start, pos);
            if (pos < length) pos++;
            return decodeEntities(value);
        }
        // 无引号值
        int start = pos;
        while (pos < length) {
            char c = peek();
            if (c == ' ' || c == '>' || c == '/' || c == '\n' || c == '\r' || c == '\t') break;
            pos++;
        }
        return decodeEntities(html.substring(start, pos));
    }

    /** 解析文本节点，处理 HTML 实体 */
    private String parseText() {
        StringBuilder sb = new StringBuilder();
        while (pos < length && peek() != '<') {
            char c = peek();
            if (c == '&') {
                sb.append(parseEntity());
            } else {
                sb.append(c);
                pos++;
            }
        }
        return sb.toString();
    }

    /** 解析单个 HTML 实体（如 &amp; &lt; &nbsp;） */
    private String parseEntity() {
        int start = pos;
        pos++; // 跳过 &
        int semi = html.indexOf(';', pos);
        if (semi > 0 && semi - pos <= 10) {
            String entity = html.substring(pos, semi);
            pos = semi + 1;
            return decodeEntity(entity);
        }
        pos = start + 1;
        return "&";
    }

    /** 解码字符串中所有 HTML 实体 */
    private String decodeEntities(String s) {
        if (s.indexOf('&') < 0) return s;
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '&') {
                int semi = s.indexOf(';', i);
                if (semi > 0 && semi - i <= 11) {
                    String entity = s.substring(i + 1, semi);
                    String decoded = decodeEntity(entity);
                    if (decoded != null) {
                        sb.append(decoded);
                        i = semi + 1;
                        continue;
                    }
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    /** 实体名转字符，支持十进制(&#9664;)、十六进制(&#x25C0;)和常用命名实体 */
    private String decodeEntity(String entity) {
        // 十进制数字实体
        if (entity.startsWith("#")) {
            try {
                int codePoint = Integer.parseInt(entity.substring(1));
                return new String(Character.toChars(codePoint));
            } catch (NumberFormatException e) {
                return "&" + entity + ";";
            }
        }
        // 十六进制数字实体
        if (entity.startsWith("x") || entity.startsWith("X")) {
            try {
                int codePoint = Integer.parseInt(entity.substring(1), 16);
                return new String(Character.toChars(codePoint));
            } catch (NumberFormatException e) {
                return "&" + entity + ";";
            }
        }
        // 命名实体
        return switch (entity) {
            case "lt" -> "<";
            case "gt" -> ">";
            case "amp" -> "&";
            case "quot" -> "\"";
            case "apos" -> "'";
            case "nbsp" -> " ";
            case "middot" -> "·";
            case "bull" -> "•";
            case "lsaquo" -> "‹";
            case "rsaquo" -> "›";
            case "laquo" -> "«";
            case "raquo" -> "»";
            case "darr" -> "↓";
            case "uarr" -> "↑";
            case "harr" -> "↔";
            case "larr" -> "←";
            case "rarr" -> "→";
            case "clubs" -> "♣";
            case "hearts" -> "♥";
            case "diams" -> "♦";
            case "spades" -> "♠";
            case "starf" -> "★";
            case "star" -> "☆";
            case "ldots", "hellip" -> "…";
            default -> "&" + entity + ";";
        };
    }

    /** 跳过注释 <!-- ... --> */
    private void skipComment() {
        pos += 4; // 跳过 <!--
        int end = html.indexOf("-->", pos);
        pos = end >= 0 ? end + 3 : length;
    }

    /** 跳过 script/style 标签及其内容 */
    private void skipUnsupportedTag() {
        boolean isScript = startsWithIgnoreCase("<script");
        String tagName = isScript ? "script" : "style";
        skipUntilChar('>');
        if (pos < length) pos++;
        String closeTag = "</" + tagName;
        int end = indexOfIgnoreCase(closeTag, pos);
        if (end >= 0) {
            pos = end;
            skipUntilChar('>');
            if (pos < length) pos++;
        } else {
            pos = length;
        }
    }

    private void skipWhitespace() {
        while (pos < length) {
            char c = peek();
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                pos++;
            } else {
                break;
            }
        }
    }

    private void skipUntilChar(char target) {
        while (pos < length && peek() != target) pos++;
    }

    private char peek() {
        return html.charAt(pos);
    }

    private boolean startsWith(String prefix) {
        return html.startsWith(prefix, pos);
    }

    private boolean startsWithIgnoreCase(String prefix) {
        if (pos + prefix.length() > length) return false;
        for (int i = 0; i < prefix.length(); i++) {
            if (Character.toLowerCase(html.charAt(pos + i)) != Character.toLowerCase(prefix.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private int indexOfIgnoreCase(String target, int from) {
        String lower = html.toLowerCase(Locale.ROOT);
        return lower.indexOf(target.toLowerCase(Locale.ROOT), from);
    }

    /** 递归查找第一个指定标签的元素 */
    private HtmlElement findFirstByTag(HtmlElement root, String tag) {
        if (root.tagName().equals(tag)) return root;
        for (HtmlNode child : root.children()) {
            if (child.isElement()) {
                HtmlElement found = findFirstByTag((HtmlElement) child, tag);
                if (found != null) return found;
            }
        }
        return null;
    }
}
