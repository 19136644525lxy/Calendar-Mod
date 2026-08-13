package com.calendar.mod.client;

import com.calendar.mod.calendar.CalendarConfig;
import com.calendar.mod.calendar.CalendarDate;
import com.calendar.mod.calendar.CalendarSystem;
import com.calendar.mod.data.CalendarEvent;
import com.calendar.mod.network.CalendarClientCache;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Language;

import java.util.ArrayList;
import java.util.List;

public class CalendarHud {
    public static final CalendarHud INSTANCE = new CalendarHud();

    private volatile boolean visible = true;
    private boolean dragging = false;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;

    /** HUD 圆角半径（像素），保持现代化观感 */
    private static final int RADIUS = 8;
    /** HUD 阴影偏移和模糊（用多层外扩矩形模拟） */
    private static final int SHADOW_OFFSET_Y = 2;
    private static final int SHADOW_BLUR = 4;

    private CalendarHud() {}

    public boolean isVisible() { return visible && CalendarClientConfig.hudEnabled; }
    public void setVisible(boolean v) { visible = v; }
    public void toggle() { visible = !visible; }
    public boolean isDragging() { return dragging; }

    public boolean isMouseOver(int mouseX, int mouseY, int screenWidth, int screenHeight) {
        if (!isVisible()) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return false;

        List<String> lines = buildLines();
        int[] size = computeHudSize(lines, mc.textRenderer);
        int[] pos = computePosition(screenWidth, screenHeight, size[0], size[1]);
        // 命中测试包含阴影外扩区域（更友好）
        int pad = SHADOW_BLUR;
        return mouseX >= pos[0] - pad && mouseX <= pos[0] + size[0] + pad
            && mouseY >= pos[1] - pad && mouseY <= pos[1] + size[1] + pad;
    }

    public void startDrag(int mouseX, int mouseY, int screenWidth, int screenHeight) {
        if (!isVisible()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        List<String> lines = buildLines();
        int[] size = computeHudSize(lines, mc.textRenderer);
        int[] pos = computePosition(screenWidth, screenHeight, size[0], size[1]);
        dragging = true;
        dragOffsetX = mouseX - pos[0];
        dragOffsetY = mouseY - pos[1];
    }

    public void drag(int mouseX, int mouseY, int screenWidth, int screenHeight) {
        if (!dragging) return;
        int newX = mouseX - dragOffsetX;
        int newY = mouseY - dragOffsetY;
        newX = Math.max(0, Math.min(newX, screenWidth - 50));
        newY = Math.max(0, Math.min(newY, screenHeight - 30));
        if (CalendarClientConfig.hudRightAlign) {
            CalendarClientConfig.hudX = screenWidth - newX;
        } else {
            CalendarClientConfig.hudX = newX;
        }
        CalendarClientConfig.hudY = newY;
        CalendarClientConfig.save();
    }

    public void stopDrag() {
        dragging = false;
    }

    public void render(DrawContext drawContext, int screenWidth, int screenHeight) {
        if (!isVisible()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        TextRenderer font = mc.textRenderer;
        int lineHeight = font.fontHeight + 4;

        List<String> lines = buildLines();
        int[] size = computeHudSize(lines, font);
        int width = size[0];
        int height = size[1];

        int[] pos = computePosition(screenWidth, screenHeight, width, height);
        int x = pos[0], y = pos[1];

        // 从当前选中的样式读取 HUD 颜色配置
        StyleManager.HudColors hud;
        try {
            StyleManager.StyleInfo style = StyleManager.getById(CalendarClientConfig.selectedStyle);
            hud = style.hudColors;
        } catch (Exception e) {
            // 回退到默认配色
            hud = new StyleManager.HudColors(
                    0x28000000, 0xE8F7F7F8, 0xFFE4E7EC, 0x1A000000,
                    0xFF1E293B, 0xFF475569, 0xFFB45309);
        }

        // ========== 1. 柔和阴影（外扩多层半透明深色） ==========
        int shadowBaseAlpha = (hud.shadow >>> 24) & 0xFF;
        int shadowRgb = hud.shadow & 0x00FFFFFF;
        for (int layer = SHADOW_BLUR; layer >= 1; layer--) {
            int alphaStep = (int) ((shadowBaseAlpha * (long) layer) / SHADOW_BLUR);
            if (alphaStep > 0xFF) alphaStep = 0xFF;
            int col = (alphaStep << 24) | shadowRgb;
            int sx = x - layer;
            int sy = y - layer + SHADOW_OFFSET_Y;
            int sw = width + layer * 2;
            int sh = height + layer * 2;
            drawRoundedFill(drawContext, sx, sy, sw, sh, RADIUS + layer, col);
        }

        // ========== 2. 半透明主体 + 顶部装饰条（整体圆角） ==========
        drawRoundedFill(drawContext, x, y, width, height, RADIUS, hud.body);

        // ========== 3. 顶部高亮条（仅顶部圆角内的一条装饰带） ==========
        int decorH = 3;
        drawContext.fill(x + RADIUS, y, x + width - RADIUS, y + decorH, hud.decor);
        fillCornerBand(drawContext, x + RADIUS, y + RADIUS, RADIUS, decorH, hud.decor, false, false);
        fillCornerBand(drawContext, x + width - RADIUS, y + RADIUS, RADIUS, decorH, hud.decor, true, false);

        // ========== 4. 细边框（比背景深一点，增强立体感） ==========
        drawRoundedBorder(drawContext, x, y, width, height, RADIUS, 1, hud.border);

        // ========== 5. 文本渲染 ==========
        int padX = 12;
        int padY = 8;
        int ty = y + decorH + padY;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int color;
            if (line.startsWith("\u25C6")) {
                color = hud.textEvent;
            } else if (i == 0) {
                color = hud.textPrimary;
            } else {
                color = hud.textSecondary;
            }
            drawContext.drawText(font, line, x + padX, ty, color, false);
            ty += lineHeight;
        }
    }

    /** 计算 HUD 尺寸（宽度、高度），保证渲染与命中测试一致 */
    private int[] computeHudSize(List<String> lines, TextRenderer font) {
        int padX = 12;
        int padY = 8;
        int decorH = 3;
        int lineHeight = font.fontHeight + 4;
        int contentWidth = 0;
        for (String line : lines) {
            contentWidth = Math.max(contentWidth, font.getWidth(line));
        }
        int width = contentWidth + padX * 2;
        int height = lines.size() * lineHeight + padY * 2 + decorH;
        return new int[]{width, height};
    }

    // ============= 圆角绘制辅助方法 =============

    /** 圆角矩形填充：中心矩形 + 上下直边 + 四角 1/4 圆 */
    private static void drawRoundedFill(DrawContext g, int x, int y, int w, int h, int r, int color) {
        if ((color >>> 24) == 0) return;
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) { g.fill(x, y, x + w, y + h, color); return; }
        int x2 = x + w, y2 = y + h;
        g.fill(x + r, y, x2 - r, y + r, color);       // 上直边
        g.fill(x + r, y2 - r, x2 - r, y2, color);     // 下直边
        g.fill(x, y + r, x2, y2 - r, color);          // 中间
        drawCorner(g, x + r, y + r, r, color, false, false);
        drawCorner(g, x2 - r, y + r, r, color, true, false);
        drawCorner(g, x + r, y2 - r, r, color, false, true);
        drawCorner(g, x2 - r, y2 - r, r, color, true, true);
    }

    private static void drawCorner(DrawContext g, int cx, int cy, int r, int color,
                                   boolean rx, boolean ry) {
        int rr = r * r;
        for (int dy = 0; dy < r; dy++) {
            int dy2 = ry ? dy : (r - 1 - dy);
            int yLine = cy + (ry ? dy : -dy);
            int dxMax = (int) Math.sqrt(rr - dy2 * dy2);
            if (dxMax < 0) continue;
            if (rx) g.fill(cx, yLine, cx + dxMax, yLine + 1, color);
            else    g.fill(cx - dxMax, yLine, cx, yLine + 1, color);
        }
    }

    /** 顶部装饰带的圆角区填充（仅前 dy < decorH 的行） */
    private static void fillCornerBand(DrawContext g, int cx, int cy, int r, int decorH,
                                        int color, boolean rx, boolean ry) {
        int rr = r * r;
        for (int dy = 0; dy < decorH && dy < r; dy++) {
            int dy2 = ry ? dy : (r - 1 - dy);
            int yLine = cy + (ry ? dy : -dy);
            int dxMax = (int) Math.sqrt(rr - dy2 * dy2);
            if (dxMax < 0) continue;
            if (rx) g.fill(cx, yLine, cx + dxMax, yLine + 1, color);
            else    g.fill(cx - dxMax, yLine, cx, yLine + 1, color);
        }
    }

    /** 圆角边框（8 段式：4 直边 + 4 角弧） */
    private static void drawRoundedBorder(DrawContext g, int x, int y, int w, int h,
                                          int r, int bw, int color) {
        if ((color >>> 24) == 0 || bw <= 0) return;
        int x2 = x + w, y2 = y + h;
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) {
            g.fill(x, y, x2, y + bw, color);
            g.fill(x, y2 - bw, x2, y2, color);
            g.fill(x, y, x + bw, y2, color);
            g.fill(x2 - bw, y, x2, y2, color);
            return;
        }
        if (bw <= r) {
            g.fill(x + r, y, x2 - r, y + bw, color);
            g.fill(x + r, y2 - bw, x2 - r, y2, color);
            g.fill(x, y + r, x + bw, y2 - r, color);
            g.fill(x2 - bw, y + r, x2, y2 - r, color);
            drawRingCorner(g, x + r, y + r, r, bw, color, false, false);
            drawRingCorner(g, x2 - r, y + r, r, bw, color, true, false);
            drawRingCorner(g, x + r, y2 - r, r, bw, color, false, true);
            drawRingCorner(g, x2 - r, y2 - r, r, bw, color, true, true);
        } else {
            drawRoundedFill(g, x, y, w, h, r, color);
        }
    }

    private static void drawRingCorner(DrawContext g, int cx, int cy, int r, int bw, int color,
                                       boolean rx, boolean ry) {
        int rOut2 = r * r;
        int rIn2 = (r - bw) * (r - bw);
        for (int dy = 0; dy < r; dy++) {
            int dy2 = ry ? dy : (r - 1 - dy);
            int yLine = cy + (ry ? dy : -dy);
            int d2 = dy2 * dy2;
            int dxOut = (int) Math.sqrt(rOut2 - d2);
            int dxIn = (int) Math.ceil(Math.sqrt(Math.max(0, rIn2 - d2)));
            if (dxOut > dxIn) {
                if (rx) g.fill(cx + dxIn, yLine, cx + dxOut, yLine + 1, color);
                else    g.fill(cx - dxOut, yLine, cx - dxIn, yLine + 1, color);
            }
        }
    }

    private List<String> buildLines() {
        List<String> lines = new ArrayList<>();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return lines;

        CalendarDate today = CalendarClientCache.INSTANCE.getCurrentDate();
        CalendarConfig cfg = CalendarClientCache.INSTANCE.getConfig();
        CalendarSystem sys = CalendarClientCache.INSTANCE.getSystem();
        CalendarDate dayZero = CalendarClientCache.INSTANCE.getDayZero();

        int wc = Math.max(1, cfg.getWeekdaysCount());
        long deltaDays = sys.toWorldDays(today, dayZero);
        int weekdayIndex = (int) ((deltaDays % wc + wc) % wc);
        String weekdayName = cfg.getWeekdayName(weekdayIndex);

        lines.add(cfg.getEraName() + " " + today.getYear() + " "
                + Language.getInstance().get("calendarmod.label.year")
                + " " + sys.getMonthName(today.getMonth()));

        lines.add(today.getDay() + "  " + weekdayName);

        if (CalendarClientConfig.showTodayEvents) {
            List<CalendarEvent> todayEvents = getTodayEvents(today);
            int maxEvents = CalendarClientConfig.showEventDescriptions ? 2 : 3;
            for (int i = 0; i < Math.min(todayEvents.size(), maxEvents); i++) {
                CalendarEvent ev = todayEvents.get(i);
                String line = "◆ " + ev.getName();
                if (CalendarClientConfig.showEventDescriptions && ev.getDescription() != null
                        && !ev.getDescription().isEmpty()) {
                    line += " - " + ev.getDescription();
                }
                lines.add(line);
            }
        }

        return lines;
    }

    private int[] computePosition(int screenWidth, int screenHeight, int width, int height) {
        int x, y;
        if (CalendarClientConfig.hudRightAlign) {
            x = screenWidth - width - CalendarClientConfig.hudX;
        } else {
            x = CalendarClientConfig.hudX;
        }
        y = CalendarClientConfig.hudY;
        x = Math.max(0, Math.min(x, screenWidth - width));
        y = Math.max(0, Math.min(y, screenHeight - height));
        return new int[]{x, y};
    }

    private List<CalendarEvent> getTodayEvents(CalendarDate today) {
        List<CalendarEvent> result = new ArrayList<>();
        for (CalendarEvent ev : CalendarClientCache.INSTANCE.getEvents()) {
            CalendarDate ed = ev.getDate();
            if (ed == null) continue;
            boolean matches;
            if (ev.isFixed()) {
                matches = (ed.getMonth() == today.getMonth() && ed.getDay() == today.getDay());
            } else {
                matches = ed.equals(today);
            }
            if (matches) result.add(ev);
        }
        return result;
    }
}
