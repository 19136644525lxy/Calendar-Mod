package com.htmlcraft.api.binding;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 模板引擎。
 * 处理 HTML 模板中的 {{variable}} 变量替换。
 */
public class TemplateEngine {

    // 匹配 {{variable}} 模式
    private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{(\\w+)\\}\\}");

    /**
     * 渲染模板：将 {{variable}} 替换为 DataContext 中的值。
     */
    public static String render(String template, DataContext context) {
        if (template == null || template.isEmpty()) return "";
        if (context == null) return template;

        Matcher matcher = VAR_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = context.getString(key);
            // 转义 HTML 特殊字符（防止注入），并转义 $ 和 \ 避免被 appendReplacement 当作特殊字符
            matcher.appendReplacement(result, Matcher.quoteReplacement(escapeHtml(value)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /** HTML 特殊字符转义 */
    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }
}
