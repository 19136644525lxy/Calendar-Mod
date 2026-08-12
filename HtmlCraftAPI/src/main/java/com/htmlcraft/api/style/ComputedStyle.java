package com.htmlcraft.api.style;

import java.util.Arrays;

/**
 * 计算后的样式，存储元素最终计算出的所有样式属性值。
 * 每个属性都有默认值，通过 {@link #DEFAULT} 静态常量提供默认参考。
 */
public class ComputedStyle {

    /** 显示类型 */
    public enum DisplayType {
        BLOCK, FLEX, INLINE, GRID, NONE
    }

    /** 弹性布局方向 */
    public enum FlexDirection {
        ROW, COLUMN
    }

    /** 主轴对齐方式 */
    public enum JustifyContent {
        FLEX_START, CENTER, FLEX_END, SPACE_BETWEEN, SPACE_AROUND
    }

    /** 交叉轴对齐方式 */
    public enum AlignItems {
        FLEX_START, CENTER, FLEX_END, STRETCH
    }

    /** 文本对齐方式 */
    public enum TextAlign {
        LEFT, CENTER, RIGHT
    }

    /** 定位类型 */
    public enum PositionType {
        STATIC, RELATIVE, ABSOLUTE, FIXED
    }

    /** 溢出处理 */
    public enum Overflow {
        VISIBLE, HIDDEN, SCROLL, AUTO
    }

    /** 渐变方向 */
    public enum GradientDir {
        NONE, TO_BOTTOM, TO_TOP, TO_LEFT, TO_RIGHT
    }

    /** 线性渐变（2色），dir=NONE 表示不使用渐变 */
    public static class LinearGradient {
        public GradientDir dir = GradientDir.NONE;
        public int colorStart = 0;
        public int colorEnd = 0;

        public LinearGradient() {}
        public LinearGradient(GradientDir dir, int s, int e) {
            this.dir = dir; this.colorStart = s; this.colorEnd = e;
        }
    }

    /** BoxShadow（单层） */
    public static class BoxShadow {
        public int offsetX;
        public int offsetY;
        public int blur;   // 模拟为多圈扩张
        public int spread;
        public int color;

        public BoxShadow() {}
        public BoxShadow(int ox, int oy, int blur, int spread, int color) {
            this.offsetX = ox; this.offsetY = oy; this.blur = blur;
            this.spread = spread; this.color = color;
        }
    }

    /** 默认样式实例（只读，不可修改） */
    public static final ComputedStyle DEFAULT = new ComputedStyle(true);

    // ===== 颜色（ARGB 格式） =====
    private int color = 0xFFFFFFFF;            // 文字颜色，默认白色
    private int backgroundColor = 0x00000000;  // 背景颜色，默认透明
    private int borderColor = 0xFF000000;      // 边框颜色，默认黑色

    // ===== 尺寸（-1 表示 auto） =====
    private int width = -1;
    private int height = -1;

    // ===== 内外边距（上右下左顺序） =====
    private int[] padding = new int[4];
    private int[] margin = new int[4];

    // ===== 边框 =====
    private int borderWidth = 0;

    // ===== 布局 =====
    private DisplayType display = DisplayType.BLOCK;
    private FlexDirection flexDirection = FlexDirection.ROW;
    private JustifyContent justifyContent = JustifyContent.FLEX_START;
    private AlignItems alignItems = AlignItems.FLEX_START;
    private int gap = 0;
    private int gridColumns = 0;  // Grid 列数（0 = 非网格）

    // ===== 定位 & 溢出 =====
    private PositionType position = PositionType.STATIC;
    private Overflow overflowX = Overflow.VISIBLE;
    private Overflow overflowY = Overflow.VISIBLE;
    private int zIndex = 0;
    private int left = Integer.MIN_VALUE;   // Integer.MIN_VALUE 表示未设置
    private int top = Integer.MIN_VALUE;
    private int right = Integer.MIN_VALUE;
    private int bottom = Integer.MIN_VALUE;

    // ===== 视觉效果 =====
    private int borderRadius = 0;              // 统一圆角半径（像素）
    private LinearGradient linearGradient = new LinearGradient();
    private BoxShadow boxShadow = null;        // null 表示无阴影

    // ===== 文本 =====
    private int fontSize = 9; // MC 标准字体大小
    private TextAlign textAlign = TextAlign.LEFT;

    // ===== 透明度 =====
    private float opacity = 1.0f;

    /** 只读标记，用于保护 DEFAULT 实例 */
    private final boolean readonly;

    /** 创建可写的默认样式实例 */
    public ComputedStyle() {
        this.readonly = false;
    }

    /** 私有构造，用于创建 DEFAULT */
    private ComputedStyle(boolean readonly) {
        this.readonly = readonly;
    }

    /** 复制构造，创建可写副本 */
    public ComputedStyle(ComputedStyle other) {
        this.color = other.color;
        this.backgroundColor = other.backgroundColor;
        this.borderColor = other.borderColor;
        this.width = other.width;
        this.height = other.height;
        this.padding = Arrays.copyOf(other.padding, 4);
        this.margin = Arrays.copyOf(other.margin, 4);
        this.borderWidth = other.borderWidth;
        this.display = other.display;
        this.flexDirection = other.flexDirection;
        this.justifyContent = other.justifyContent;
        this.alignItems = other.alignItems;
        this.gap = other.gap;
        this.gridColumns = other.gridColumns;
        this.position = other.position;
        this.overflowX = other.overflowX;
        this.overflowY = other.overflowY;
        this.zIndex = other.zIndex;
        this.left = other.left;
        this.top = other.top;
        this.right = other.right;
        this.bottom = other.bottom;
        this.borderRadius = other.borderRadius;
        this.linearGradient = new LinearGradient(
            other.linearGradient.dir, other.linearGradient.colorStart, other.linearGradient.colorEnd);
        this.boxShadow = other.boxShadow == null ? null :
            new BoxShadow(other.boxShadow.offsetX, other.boxShadow.offsetY,
                other.boxShadow.blur, other.boxShadow.spread, other.boxShadow.color);
        this.fontSize = other.fontSize;
        this.textAlign = other.textAlign;
        this.opacity = other.opacity;
        this.readonly = false;
    }

    /** 检查可写性，保护 DEFAULT 实例 */
    private void checkWritable() {
        if (readonly) {
            throw new UnsupportedOperationException("DEFAULT 样式实例不可修改");
        }
    }

    // ===== Color Getter / Setter =====
    public int getColor() { return color; }
    public void setColor(int color) { checkWritable(); this.color = color; }

    public int getBackgroundColor() { return backgroundColor; }
    public void setBackgroundColor(int backgroundColor) { checkWritable(); this.backgroundColor = backgroundColor; }

    public int getBorderColor() { return borderColor; }
    public void setBorderColor(int borderColor) { checkWritable(); this.borderColor = borderColor; }

    // ===== Size =====
    public int getWidth() { return width; }
    public void setWidth(int width) { checkWritable(); this.width = width; }

    public int getHeight() { return height; }
    public void setHeight(int height) { checkWritable(); this.height = height; }

    // ===== Padding =====
    public int[] getPadding() { return Arrays.copyOf(padding, 4); }
    public int getPadding(int index) { return padding[index]; }
    public void setPadding(int[] padding) {
        checkWritable();
        if (padding == null || padding.length != 4) throw new IllegalArgumentException("padding 必须是长度为 4 的数组");
        this.padding = Arrays.copyOf(padding, 4);
    }
    public void setPadding(int top, int right, int bottom, int left) {
        checkWritable();
        this.padding[0] = top; this.padding[1] = right; this.padding[2] = bottom; this.padding[3] = left;
    }

    // ===== Margin =====
    public int[] getMargin() { return Arrays.copyOf(margin, 4); }
    public int getMargin(int index) { return margin[index]; }
    public void setMargin(int[] margin) {
        checkWritable();
        if (margin == null || margin.length != 4) throw new IllegalArgumentException("margin 必须是长度为 4 的数组");
        this.margin = Arrays.copyOf(margin, 4);
    }
    public void setMargin(int top, int right, int bottom, int left) {
        checkWritable();
        this.margin[0] = top; this.margin[1] = right; this.margin[2] = bottom; this.margin[3] = left;
    }

    // ===== Border =====
    public int getBorderWidth() { return borderWidth; }
    public void setBorderWidth(int borderWidth) { checkWritable(); this.borderWidth = borderWidth; }

    // ===== Display / Layout =====
    public DisplayType getDisplay() { return display; }
    public void setDisplay(DisplayType display) { checkWritable(); this.display = display == null ? DisplayType.BLOCK : display; }

    public FlexDirection getFlexDirection() { return flexDirection; }
    public void setFlexDirection(FlexDirection flexDirection) { checkWritable(); this.flexDirection = flexDirection == null ? FlexDirection.ROW : flexDirection; }

    public JustifyContent getJustifyContent() { return justifyContent; }
    public void setJustifyContent(JustifyContent justifyContent) { checkWritable(); this.justifyContent = justifyContent == null ? JustifyContent.FLEX_START : justifyContent; }

    public AlignItems getAlignItems() { return alignItems; }
    public void setAlignItems(AlignItems alignItems) { checkWritable(); this.alignItems = alignItems == null ? AlignItems.FLEX_START : alignItems; }

    public int getGap() { return gap; }
    public void setGap(int gap) { checkWritable(); this.gap = gap; }

    public int getGridColumns() { return gridColumns; }
    public void setGridColumns(int gridColumns) { checkWritable(); this.gridColumns = Math.max(0, gridColumns); }

    // ===== Position / Overflow =====
    public PositionType getPosition() { return position; }
    public void setPosition(PositionType p) { checkWritable(); this.position = p == null ? PositionType.STATIC : p; }

    public Overflow getOverflowX() { return overflowX; }
    public void setOverflowX(Overflow o) { checkWritable(); this.overflowX = o == null ? Overflow.VISIBLE : o; }

    public Overflow getOverflowY() { return overflowY; }
    public void setOverflowY(Overflow o) { checkWritable(); this.overflowY = o == null ? Overflow.VISIBLE : o; }

    public int getZIndex() { return zIndex; }
    public void setZIndex(int z) { checkWritable(); this.zIndex = z; }

    public int getLeft() { return left; }
    public void setLeft(int v) { checkWritable(); this.left = v; }

    public int getTop() { return top; }
    public void setTop(int v) { checkWritable(); this.top = v; }

    public int getRight() { return right; }
    public void setRight(int v) { checkWritable(); this.right = v; }

    public int getBottom() { return bottom; }
    public void setBottom(int v) { checkWritable(); this.bottom = v; }

    // ===== Visual =====
    public int getBorderRadius() { return borderRadius; }
    public void setBorderRadius(int r) { checkWritable(); this.borderRadius = Math.max(0, r); }

    public LinearGradient getLinearGradient() { return linearGradient; }
    public void setLinearGradient(LinearGradient g) { checkWritable(); this.linearGradient = g == null ? new LinearGradient() : g; }

    public BoxShadow getBoxShadow() { return boxShadow; }
    public void setBoxShadow(BoxShadow s) { checkWritable(); this.boxShadow = s; }

    // ===== Text =====
    public int getFontSize() { return fontSize; }
    public void setFontSize(int fontSize) { checkWritable(); this.fontSize = fontSize; }

    public TextAlign getTextAlign() { return textAlign; }
    public void setTextAlign(TextAlign textAlign) { checkWritable(); this.textAlign = textAlign == null ? TextAlign.LEFT : textAlign; }

    // ===== Opacity =====
    public float getOpacity() { return opacity; }
    public void setOpacity(float opacity) { checkWritable(); this.opacity = Math.max(0.0f, Math.min(1.0f, opacity)); }
}
