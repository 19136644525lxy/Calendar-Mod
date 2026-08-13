package com.htmlcraft.api.style;

import com.htmlcraft.api.core.HtmlElement;

import java.util.Locale;
import java.util.Map;

/**
 * 样式计算器。
 * 递归遍历 DOM 树，为每个 {@link HtmlElement} 计算最终的 {@link ComputedStyle}。
 *
 * 计算流程：
 * <ol>
 *   <li>从 {@link StyleSheet} 获取匹配的属性</li>
 *   <li>解析每个属性值（颜色、尺寸、枚举等）</li>
 *   <li>设置到元素的 ComputedStyle 上</li>
 *   <li>递归处理子元素</li>
 * </ol>
 */
public final class StyleCalculator {

    /** 百分比尺寸标记值（用于 width/height 等属性） */
    public static final int PERCENT = Integer.MIN_VALUE;

    private StyleCalculator() {
        // 工具类，禁止实例化
    }

    /**
     * 计算 DOM 树中所有元素的样式。
     *
     * @param root        根元素
     * @param styleSheet  样式表
     */
    public static void computeStyles(HtmlElement root, StyleSheet styleSheet) {
        if (root == null || styleSheet == null) {
            return;
        }
        computeRecursive(root, styleSheet);
    }

    /** 递归计算单个元素及其子元素 */
    private static void computeRecursive(HtmlElement element, StyleSheet styleSheet) {
        computeSingle(element, styleSheet);
        for (HtmlElement child : element.getChildren()) {
            computeRecursive(child, styleSheet);
        }
    }

    /** 计算单个元素的样式 */
    private static void computeSingle(HtmlElement element, StyleSheet styleSheet) {
        Map<String, String> props = styleSheet.match(element);
        ComputedStyle style = new ComputedStyle();

        // ===== 颜色 =====
        if (props.containsKey("color")) {
            style.setColor(parseColor(props.get("color"), 0xFFFFFFFF));
        }
        if (props.containsKey("background-color")) {
            style.setBackgroundColor(parseColor(props.get("background-color"), 0x00000000));
        } else if (props.containsKey("background")) {
            // 简写形式回退
            style.setBackgroundColor(parseColor(props.get("background"), 0x00000000));
        }
        if (props.containsKey("border-color")) {
            style.setBorderColor(parseColor(props.get("border-color"), 0xFF000000));
        }

        // ===== 尺寸 =====
        if (props.containsKey("width")) {
            style.setWidth(parseSize(props.get("width")));
        }
        if (props.containsKey("height")) {
            style.setHeight(parseSize(props.get("height")));
        }

        // ===== 内外边距 =====
        if (props.containsKey("padding")) {
            applyBox(props.get("padding"), style, true);
        }
        if (props.containsKey("margin")) {
            applyBox(props.get("margin"), style, false);
        }

        // ===== 边框 =====
        if (props.containsKey("border-width")) {
            style.setBorderWidth(parseSize(props.get("border-width")));
        }

        // ===== 显示类型 =====
        if (props.containsKey("display")) {
            style.setDisplay(parseDisplay(props.get("display")));
        }

        // ===== 弹性布局 =====
        if (props.containsKey("flex-direction")) {
            style.setFlexDirection(parseFlexDirection(props.get("flex-direction")));
        }
        if (props.containsKey("justify-content")) {
            style.setJustifyContent(parseJustifyContent(props.get("justify-content")));
        }
        if (props.containsKey("align-items")) {
            style.setAlignItems(parseAlignItems(props.get("align-items")));
        }
        if (props.containsKey("gap")) {
            style.setGap(parseSize(props.get("gap")));
        }

        // ===== 网格布局 =====
        if (props.containsKey("grid-template-columns")) {
            style.setGridColumns(parseGridColumns(props.get("grid-template-columns")));
        }

        // ===== 文本 =====
        if (props.containsKey("font-size")) {
            style.setFontSize(parseSize(props.get("font-size")));
        }
        if (props.containsKey("text-align")) {
            style.setTextAlign(parseTextAlign(props.get("text-align")));
        }

        // ===== 透明度 =====
        if (props.containsKey("opacity")) {
            style.setOpacity(parseFloat(props.get("opacity"), 1.0f));
        }

        // ===== 定位 =====
        if (props.containsKey("position")) {
            style.setPosition(parsePosition(props.get("position")));
        }
        if (props.containsKey("left")) {
            style.setLeft(parseSize(props.get("left")));
        }
        if (props.containsKey("top")) {
            style.setTop(parseSize(props.get("top")));
        }
        if (props.containsKey("right")) {
            style.setRight(parseSize(props.get("right")));
        }
        if (props.containsKey("bottom")) {
            style.setBottom(parseSize(props.get("bottom")));
        }
        if (props.containsKey("z-index")) {
            try {
                style.setZIndex(Integer.parseInt(props.get("z-index").trim()));
            } catch (NumberFormatException ignored) { }
        }

        // ===== 溢出 =====
        if (props.containsKey("overflow")) {
            ComputedStyle.Overflow ov = parseOverflow(props.get("overflow"));
            style.setOverflowX(ov);
            style.setOverflowY(ov);
        }
        if (props.containsKey("overflow-x")) {
            style.setOverflowX(parseOverflow(props.get("overflow-x")));
        }
        if (props.containsKey("overflow-y")) {
            style.setOverflowY(parseOverflow(props.get("overflow-y")));
        }

        // ===== 圆角 =====
        if (props.containsKey("border-radius")) {
            style.setBorderRadius(parseSize(props.get("border-radius")));
        }

        // ===== 背景渐变（优先级高于 background-color） =====
        if (props.containsKey("background-image")) {
            ComputedStyle.LinearGradient grad = parseLinearGradient(props.get("background-image"));
            if (grad != null && grad.dir != ComputedStyle.GradientDir.NONE) {
                style.setLinearGradient(grad);
            }
        }
        // 也允许 background: linear-gradient(...) 简写
        String bgVal = props.get("background");
        if (bgVal != null && bgVal.toLowerCase(Locale.ROOT).startsWith("linear-gradient")) {
            ComputedStyle.LinearGradient grad = parseLinearGradient(bgVal);
            if (grad != null && grad.dir != ComputedStyle.GradientDir.NONE) {
                style.setLinearGradient(grad);
            }
        }

        // ===== 阴影 =====
        if (props.containsKey("box-shadow")) {
            ComputedStyle.BoxShadow sh = parseBoxShadow(props.get("box-shadow"));
            if (sh != null) {
                style.setBoxShadow(sh);
            }
        }

        element.setComputedStyle(style);
    }

    // ===== 属性解析方法 =====

    /**
     * 解析 padding/margin 盒模型。
     * 支持:
     * <ul>
     *   <li>"10px" - 四方向相同</li>
     *   <li>"10px 20px" - 上下 / 左右</li>
     *   <li>"10px 20px 30px" - 上 / 左右 / 下</li>
     *   <li>"10px 20px 30px 40px" - 上右下左</li>
     * </ul>
     */
    private static void applyBox(String value, ComputedStyle style, boolean isPadding) {
        if (value == null) {
            return;
        }
        String[] parts = value.trim().split("\\s+");
        int[] values = new int[4];
        switch (parts.length) {
            case 1:
                values[0] = values[1] = values[2] = values[3] = parseSize(parts[0]);
                break;
            case 2:
                values[0] = values[2] = parseSize(parts[0]); // 上下
                values[1] = values[3] = parseSize(parts[1]); // 左右
                break;
            case 3:
                values[0] = parseSize(parts[0]);                  // 上
                values[1] = values[3] = parseSize(parts[1]);      // 左右
                values[2] = parseSize(parts[2]);                  // 下
                break;
            default:
                values[0] = parseSize(parts[0]); // 上
                values[1] = parseSize(parts[1]); // 右
                values[2] = parseSize(parts[2]); // 下
                values[3] = parseSize(parts[3]); // 左
                break;
        }
        if (isPadding) {
            style.setPadding(values);
        } else {
            style.setMargin(values);
        }
    }

    /**
     * 解析颜色值。
     * 支持: #RGB, #RRGGBB, #RRGGBBAA, 以及常用颜色名
     * (black, white, red, green, blue, gray, transparent)。
     *
     * @param value        颜色字符串
     * @param defaultValue 解析失败时的默认值
     * @return ARGB 格式的颜色值
     */
    public static int parseColor(String value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        switch (trimmed) {
            case "black":       return 0xFF000000;
            case "white":       return 0xFFFFFFFF;
            case "red":         return 0xFFFF0000;
            case "green":       return 0xFF00FF00;
            case "blue":        return 0xFF0000FF;
            case "gray":        return 0xFF808080;
            case "transparent": return 0x00000000;
            default: break;
        }

        // rgba() 和 rgb() 格式
        if (trimmed.startsWith("rgba(") && trimmed.endsWith(")")) {
            return parseRgbColor(trimmed.substring(5, trimmed.length() - 1), true, defaultValue);
        }
        if (trimmed.startsWith("rgb(") && trimmed.endsWith(")")) {
            return parseRgbColor(trimmed.substring(4, trimmed.length() - 1), false, defaultValue);
        }

        if (!trimmed.startsWith("#")) {
            return defaultValue;
        }
        String hex = trimmed.substring(1);
        try {
            switch (hex.length()) {
                case 3: { // #RGB -> #RRGGBB
                    int r = Character.digit(hex.charAt(0), 16);
                    int g = Character.digit(hex.charAt(1), 16);
                    int b = Character.digit(hex.charAt(2), 16);
                    r = r * 16 + r;
                    g = g * 16 + g;
                    b = b * 16 + b;
                    return 0xFF000000 | (r << 16) | (g << 8) | b;
                }
                case 6: { // #RRGGBB
                    int rgb = Integer.parseInt(hex, 16);
                    return 0xFF000000 | rgb;
                }
                case 8: { // #RRGGBBAA -> ARGB (0xAARRGGBB)
                    int r = Integer.parseInt(hex.substring(0, 2), 16);
                    int g = Integer.parseInt(hex.substring(2, 4), 16);
                    int b = Integer.parseInt(hex.substring(4, 6), 16);
                    int a = Integer.parseInt(hex.substring(6, 8), 16);
                    return (a << 24) | (r << 16) | (g << 8) | b;
                }
                default:
                    return defaultValue;
            }
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 解析 rgb() 或 rgba() 格式的颜色值。
     * 例如: "0, 0, 0" 或 "0, 0, 0, 0.5"
     */
    private static int parseRgbColor(String inner, boolean hasAlpha, int defaultValue) {
        try {
            String[] parts = inner.split(",");
            if (hasAlpha && parts.length >= 4) {
                int r = clamp255(parseIntOrPercent(parts[0].trim()));
                int g = clamp255(parseIntOrPercent(parts[1].trim()));
                int b = clamp255(parseIntOrPercent(parts[2].trim()));
                float a = Math.max(0f, Math.min(1f, Float.parseFloat(parts[3].trim())));
                int alpha = (int) (a * 255);
                return (alpha << 24) | (r << 16) | (g << 8) | b;
            } else if (!hasAlpha && parts.length >= 3) {
                int r = clamp255(parseIntOrPercent(parts[0].trim()));
                int g = clamp255(parseIntOrPercent(parts[1].trim()));
                int b = clamp255(parseIntOrPercent(parts[2].trim()));
                return 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        } catch (NumberFormatException e) {
            // fall through
        }
        return defaultValue;
    }

    /** 解析整数或百分比（如 "50%" -> 128, "128" -> 128） */
    private static int parseIntOrPercent(String s) {
        if (s.endsWith("%")) {
            float p = Float.parseFloat(s.substring(0, s.length() - 1));
            return Math.round(p * 255f / 100f);
        }
        return Integer.parseInt(s);
    }

    /** 将值限制在 0-255 范围内 */
    private static int clamp255(int v) {
        return Math.max(0, Math.min(255, v));
    }

    /**
     * 解析尺寸值。
     * 支持: "10px" -> 10, "auto" -> -1, "100%" -> {@link #PERCENT}。
     *
     * @param value 尺寸字符串
     * @return 解析后的像素值，-1 表示 auto，{@link #PERCENT} 表示百分比
     */
    public static int parseSize(String value) {
        if (value == null) {
            return -1;
        }
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        if ("auto".equals(trimmed)) {
            return -1;
        }
        if (trimmed.endsWith("%")) {
            return PERCENT;
        }
        if (trimmed.endsWith("px")) {
            trimmed = trimmed.substring(0, trimmed.length() - 2).trim();
        }
        try {
            return Integer.parseInt(trimmed);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** 解析显示类型 */
    private static ComputedStyle.DisplayType parseDisplay(String value) {
        if (value == null) {
            return ComputedStyle.DisplayType.BLOCK;
        }
        switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "flex":   return ComputedStyle.DisplayType.FLEX;
            case "inline": return ComputedStyle.DisplayType.INLINE;
            case "grid":   return ComputedStyle.DisplayType.GRID;
            case "none":   return ComputedStyle.DisplayType.NONE;
            default:       return ComputedStyle.DisplayType.BLOCK;
        }
    }

    /**
     * 解析 grid-template-columns。
     * 支持: "repeat(7, 1fr)" -> 7, "1fr 1fr 1fr" -> 3
     * @return 列数（0 表示解析失败）
     */
    private static int parseGridColumns(String value) {
        if (value == null) return 0;
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        // repeat(N, 1fr) 格式
        if (trimmed.startsWith("repeat(") && trimmed.endsWith(")")) {
            String inner = trimmed.substring(7, trimmed.length() - 1);
            String[] parts = inner.split(",");
            try {
                return Integer.parseInt(parts[0].trim());
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        // "1fr 1fr 1fr" 格式：按空格分词计数
        String[] tokens = trimmed.split("\\s+");
        int count = 0;
        for (String t : tokens) {
            if (t.equals("1fr") || t.endsWith("fr") || t.endsWith("px") || t.equals("auto")) {
                count++;
            }
        }
        return count;
    }

    /** 解析弹性方向 */
    private static ComputedStyle.FlexDirection parseFlexDirection(String value) {
        if (value == null) {
            return ComputedStyle.FlexDirection.ROW;
        }
        switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "column": return ComputedStyle.FlexDirection.COLUMN;
            default:       return ComputedStyle.FlexDirection.ROW;
        }
    }

    /** 解析主轴对齐方式 */
    private static ComputedStyle.JustifyContent parseJustifyContent(String value) {
        if (value == null) {
            return ComputedStyle.JustifyContent.FLEX_START;
        }
        switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "center":        return ComputedStyle.JustifyContent.CENTER;
            case "flex-end":      return ComputedStyle.JustifyContent.FLEX_END;
            case "space-between": return ComputedStyle.JustifyContent.SPACE_BETWEEN;
            case "space-around":  return ComputedStyle.JustifyContent.SPACE_AROUND;
            default:              return ComputedStyle.JustifyContent.FLEX_START;
        }
    }

    /** 解析交叉轴对齐方式 */
    private static ComputedStyle.AlignItems parseAlignItems(String value) {
        if (value == null) {
            return ComputedStyle.AlignItems.FLEX_START;
        }
        switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "center":   return ComputedStyle.AlignItems.CENTER;
            case "flex-end": return ComputedStyle.AlignItems.FLEX_END;
            case "stretch":  return ComputedStyle.AlignItems.STRETCH;
            default:         return ComputedStyle.AlignItems.FLEX_START;
        }
    }

    /** 解析文本对齐方式 */
    private static ComputedStyle.TextAlign parseTextAlign(String value) {
        if (value == null) {
            return ComputedStyle.TextAlign.LEFT;
        }
        switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "center": return ComputedStyle.TextAlign.CENTER;
            case "right":  return ComputedStyle.TextAlign.RIGHT;
            default:       return ComputedStyle.TextAlign.LEFT;
        }
    }

    /** 解析浮点数 */
    private static float parseFloat(String value, float defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Float.parseFloat(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /** 解析定位类型 */
    private static ComputedStyle.PositionType parsePosition(String value) {
        if (value == null) return ComputedStyle.PositionType.STATIC;
        switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "relative": return ComputedStyle.PositionType.RELATIVE;
            case "absolute": return ComputedStyle.PositionType.ABSOLUTE;
            case "fixed":    return ComputedStyle.PositionType.FIXED;
            default:         return ComputedStyle.PositionType.STATIC;
        }
    }

    /** 解析溢出类型 */
    private static ComputedStyle.Overflow parseOverflow(String value) {
        if (value == null) return ComputedStyle.Overflow.VISIBLE;
        switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "hidden": return ComputedStyle.Overflow.HIDDEN;
            case "scroll": return ComputedStyle.Overflow.SCROLL;
            case "auto":   return ComputedStyle.Overflow.AUTO;
            default:       return ComputedStyle.Overflow.VISIBLE;
        }
    }

    /**
     * 解析线性渐变。
     * 支持: linear-gradient(to bottom, #A, #B)
     *       linear-gradient(180deg, #A, #B) 等价于 to bottom
     *       linear-gradient(#A, #B) 默认 to bottom
     */
    private static ComputedStyle.LinearGradient parseLinearGradient(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        // 提取函数参数内容
        int start = trimmed.indexOf('(');
        int end = trimmed.lastIndexOf(')');
        if (start < 0 || end < 0 || end < start) return null;
        String inner = trimmed.substring(start + 1, end).trim();
        if (inner.isEmpty()) return null;

        // 简易分词：按逗号分割，但保留 rgba(x,x,x,x) / rgb(x,x,x) 内部逗号
        String[] parts = splitTopLevel(inner);
        if (parts.length < 2) return null;

        ComputedStyle.GradientDir dir = ComputedStyle.GradientDir.TO_BOTTOM;
        int colorIdx = 0;

        // 第一个可能是方向
        String first = parts[0].trim().toLowerCase(Locale.ROOT);
        if (first.startsWith("to ")) {
            switch (first) {
                case "to top":    dir = ComputedStyle.GradientDir.TO_TOP;    break;
                case "to bottom": dir = ComputedStyle.GradientDir.TO_BOTTOM; break;
                case "to left":   dir = ComputedStyle.GradientDir.TO_LEFT;   break;
                case "to right":  dir = ComputedStyle.GradientDir.TO_RIGHT;  break;
                default: dir = ComputedStyle.GradientDir.TO_BOTTOM;
            }
            colorIdx = 1;
        } else if (first.endsWith("deg")) {
            try {
                double deg = Double.parseDouble(first.substring(0, first.length() - 3).trim());
                dir = degToDir(deg);
                colorIdx = 1;
            } catch (NumberFormatException ignored) { }
        }

        if (colorIdx + 1 >= parts.length) return null;
        int c1 = parseColor(parts[colorIdx], 0);
        int c2 = parseColor(parts[colorIdx + 1], 0);
        // 两色都解析失败则放弃
        if (c1 == 0 && c2 == 0) return null;

        return new ComputedStyle.LinearGradient(dir, c1, c2);
    }

    private static ComputedStyle.GradientDir degToDir(double deg) {
        // 角度标准化到 [0,360)
        double d = ((deg % 360) + 360) % 360;
        if (d >= 315 || d < 45)   return ComputedStyle.GradientDir.TO_TOP;
        if (d >= 45  && d < 135)  return ComputedStyle.GradientDir.TO_RIGHT;
        if (d >= 135 && d < 225)  return ComputedStyle.GradientDir.TO_BOTTOM;
        return ComputedStyle.GradientDir.TO_LEFT;
    }

    /** 顶层按逗号分词，不切割括号内的逗号 */
    private static String[] splitTopLevel(String s) {
        java.util.List<String> result = new java.util.ArrayList<>();
        StringBuilder sb = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == ',' && depth == 0) {
                result.add(sb.toString().trim());
                sb.setLength(0);
                continue;
            }
            sb.append(c);
        }
        if (sb.length() > 0) result.add(sb.toString().trim());
        return result.toArray(new String[0]);
    }

    /**
     * 解析 box-shadow。
     * 格式: offsetX offsetY [blur] [spread] [color] 或 color offsetX offsetY [blur] [spread]
     * 例: "0 4px 12px rgba(0,0,0,0.5)"
     */
    private static ComputedStyle.BoxShadow parseBoxShadow(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        String[] parts = splitTopLevel(value);
        if (parts.length != 1) {
            // 有顶层逗号就取第一组（单层阴影）
            value = parts[0];
        }
        // 按空白分词，rgba/rgb 保留空格
        java.util.List<String> tokens = tokenizeShadow(value);
        if (tokens.size() < 2) return null;

        int offsetX = 0, offsetY = 0, blur = 0, spread = 0, color = 0x80000000;
        int colorIdx = -1;
        int[] ints = new int[4];
        int intCount = 0;

        for (int i = 0; i < tokens.size(); i++) {
            String tok = tokens.get(i).trim();
            if (tok.startsWith("#") || tok.startsWith("rgb") || tok.startsWith("rgba")
                || isColorName(tok)) {
                color = parseColor(tok, 0x80000000);
                colorIdx = i;
                continue;
            }
            int sz = parseSize(tok);
            if (sz != -1 || tok.equals("0") || tok.equals("0px")) {
                int real = (tok.equals("0") || tok.equals("0px")) ? 0 : sz;
                if (intCount < 4) ints[intCount++] = real;
            }
        }
        if (intCount >= 2) {
            offsetX = ints[0];
            offsetY = ints[1];
            if (intCount >= 3) blur = ints[2];
            if (intCount >= 4) spread = ints[3];
        }
        if (offsetX == 0 && offsetY == 0 && blur == 0 && spread == 0) return null;
        return new ComputedStyle.BoxShadow(offsetX, offsetY, blur, spread, color);
    }

    private static boolean isColorName(String v) {
        if (v == null) return false;
        String low = v.toLowerCase(Locale.ROOT);
        return low.equals("black") || low.equals("white") || low.equals("red") || low.equals("green")
            || low.equals("blue") || low.equals("gray") || low.equals("grey")
            || low.equals("yellow") || low.equals("purple") || low.equals("cyan")
            || low.equals("transparent");
    }

    /** 分词 box-shadow 值，保留 rgba(x, x, x, x) 作为一个 token */
    private static java.util.List<String> tokenizeShadow(String s) {
        java.util.List<String> tokens = new java.util.ArrayList<>();
        StringBuilder sb = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            if ((c == ' ' || c == '\t') && depth == 0) {
                if (sb.length() > 0) {
                    tokens.add(sb.toString());
                    sb.setLength(0);
                }
            } else {
                sb.append(c);
            }
        }
        if (sb.length() > 0) tokens.add(sb.toString());
        return tokens;
    }
}
