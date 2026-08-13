package com.calendar.mod.client;

import com.calendar.mod.calendar.CalendarConfig;
import com.calendar.mod.calendar.CalendarDate;
import com.calendar.mod.calendar.CalendarSystem;
import com.calendar.mod.data.CalendarEvent;
import com.calendar.mod.network.CalendarClientCache;
import com.htmlcraft.api.screen.HtmlScreen;
import com.htmlcraft.api.screen.HtmlScreen.ClickEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.resource.Resource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 日历主界面，基于外部 HTML/CSS 模板渲染。
 * 模板文件位于 resources/assets/calendarmod/templates/。
 *
 * <p>Fabric 版差异：重写 renderBackground 实现淡色遮罩（HtmlScreen Fabric 版已处理
 * HTML 管线渲染和 drawables 渲染，无需重写 render）。
 */
public class CalendarScreen extends HtmlScreen {

    private static final String BASE_PATH = "assets/calendarmod/templates/";
    private static final String HTML_TEMPLATE = BASE_PATH + "calendar_screen.html";
    private static final String CSS_TEMPLATE = BASE_PATH + "calendar_screen.css";

    private int viewYear;
    private int viewMonth;

    public CalendarScreen() {
        super(Text.translatable("calendarmod.screen.title"));
        CalendarDate today = CalendarClientCache.INSTANCE.getCurrentDate();
        this.viewYear = today.getYear();
        this.viewMonth = today.getMonth();
        setClickHandler(this::handleClick);
    }

    @Override
    protected void init() {
        // 全屏自适应：使用屏幕宽高，CSS 中所有尺寸按比例动态计算
        setPreferredSize(this.width, this.height);
        super.init();
    }

    /** 重写背景：淡色毛玻璃遮罩（而非默认全黑遮罩） */
    @Override
    public void renderBackground(DrawContext context) {
        if (this.client != null && this.client.world != null) {
            context.fill(0, 0, this.width, this.height, 0x25FFFFFF);
        } else {
            context.fill(0, 0, this.width, this.height, 0xF0F2F5FF);
        }
    }

    private CalendarConfig getConfig() {
        return CalendarClientCache.INSTANCE.getConfig();
    }

    private CalendarSystem getSystem() {
        return CalendarClientCache.INSTANCE.getSystem();
    }

    private CalendarDate getToday() {
        return CalendarClientCache.INSTANCE.getCurrentDate();
    }

    private List<CalendarEvent> getAllEvents() {
        return CalendarClientCache.INSTANCE.getEvents();
    }

    private String i18n(String key) {
        return Language.getInstance().get(key);
    }

    private boolean matchesEvent(CalendarEvent event, CalendarDate date) {
        CalendarDate ed = event.getDate();
        if (ed == null) return false;
        if (event.isFixed()) {
            return ed.getMonth() == date.getMonth() && ed.getDay() == date.getDay();
        }
        return ed.equals(date);
    }

    private List<CalendarEvent> getEventsForDate(CalendarDate date) {
        List<CalendarEvent> result = new ArrayList<>();
        for (CalendarEvent event : getAllEvents()) {
            if (matchesEvent(event, date)) result.add(event);
        }
        return result;
    }

    private boolean hasEvent(CalendarDate date) {
        for (CalendarEvent event : getAllEvents()) {
            if (matchesEvent(event, date)) return true;
        }
        return false;
    }

    private boolean isFutureDate(CalendarDate date, CalendarDate today) {
        if (date.getYear() > today.getYear()) return true;
        if (date.getYear() < today.getYear()) return false;
        if (date.getMonth() > today.getMonth()) return true;
        if (date.getMonth() < today.getMonth()) return false;
        return date.getDay() > today.getDay();
    }

    private int computeFirstDayOffset(int year, int month) {
        CalendarSystem sys = getSystem();
        CalendarDate dayZero = CalendarClientCache.INSTANCE.getDayZero();
        CalendarDate firstDay = new CalendarDate(year, month, 1);
        long daysFromZero = sys.toWorldDays(firstDay, dayZero);
        int weekdays = getConfig().getWeekdaysCount();
        if (weekdays <= 0) weekdays = 7;
        return (int) ((daysFromZero % weekdays + weekdays) % weekdays);
    }

    /**
     * 读取模板文件内容。
     * 优先通过 ResourceManager 加载（支持资源包覆盖），失败时回退到 ClassLoader（模组 jar 自带资源）。
     * 不做静态缓存，以便玩家切换资源包后能即时生效。
     */
    private static String loadTemplate(String path) {
        // 1. 优先使用 ResourceManager（支持资源包）
        try {
            Identifier id = toIdentifier(path);
            if (id != null) {
                Optional<Resource> res = MinecraftClient.getInstance().getResourceManager().getResource(id);
                if (res.isPresent()) {
                    return readAll(res.get().getInputStream());
                }
            }
        } catch (Exception ignored) {
            // 资源包加载失败，继续回退
        }

        // 2. 回退到 ClassLoader（模组 jar 自带资源）
        try {
            InputStream is = CalendarScreen.class.getClassLoader().getResourceAsStream(path);
            if (is == null) {
                is = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
            }
            if (is == null) return "";
            return readAll(is);
        } catch (Exception e) {
            return "";
        }
    }

    /** 将 "assets/calendarmod/templates/xxx.css" 转为 Identifier("calendarmod", "templates/xxx.css") */
    private static Identifier toIdentifier(String path) {
        if (!path.startsWith("assets/")) return null;
        String trimmed = path.substring("assets/".length());
        int slash = trimmed.indexOf('/');
        if (slash <= 0) return null;
        String namespace = trimmed.substring(0, slash);
        String rest = trimmed.substring(slash + 1);
        try {
            return new Identifier(namespace, rest);
        } catch (Exception e) {
            return null;
        }
    }

    private static String readAll(InputStream is) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(line);
            }
            return sb.toString();
        }
    }

    @Override
    protected String getHtml() {
        CalendarSystem sys = getSystem();
        CalendarConfig config = getConfig();
        CalendarDate today = getToday();
        int year = viewYear;
        int month = viewMonth;
        int daysInMonth = sys.getDaysInMonth(year, month);
        String monthName = sys.getMonthName(month);
        String eraName = sys.getEraName();
        int weekdaysCount = config.getWeekdaysCount();

        // 统计本月事件数
        int monthEventCount = 0;
        for (int d = 1; d <= daysInMonth; d++) {
            if (hasEvent(new CalendarDate(year, month, d))) monthEventCount++;
        }

        // 今日星期
        CalendarDate dayZero = CalendarClientCache.INSTANCE.getDayZero();
        long deltaDays = sys.toWorldDays(today, dayZero);
        int wc = Math.max(1, weekdaysCount);
        int todayWeekday = (int) ((deltaDays % wc + wc) % wc);
        String todayWeekdayName = config.getWeekdayName(todayWeekday);

        // 读取外部模板
        String template = loadTemplate(HTML_TEMPLATE);

        // 填充模板变量
        template = template.replace("{{era_name}}", eraName);
        template = template.replace("{{year}}", String.valueOf(year));
        template = template.replace("{{year_label}}", i18n("calendarmod.label.year"));
        template = template.replace("{{month_name}}", monthName);
        template = template.replace("{{month_events}}", String.valueOf(monthEventCount));
        template = template.replace("{{today_weekday}}", todayWeekdayName);
        template = template.replace("{{today_events_label}}", i18n("calendarmod.label.today_events"));
        template = template.replace("{{client_config_label}}", i18n("calendarmod.button.client_config"));
        template = template.replace("{{calendar_config_label}}", i18n("calendarmod.button.calendar_config"));
        template = template.replace("{{add_event_label}}", i18n("calendarmod.button.add_event"));

        // 填充星期行
        StringBuilder weekRow = new StringBuilder();
        for (int i = 0; i < weekdaysCount; i++) {
            String wName = config.getWeekdayName(i);
            String cls = "cal-week-cell";
            if (i == 0) cls += " sun";
            else if (i == weekdaysCount - 1) cls += " sat";
            weekRow.append("<div class='").append(cls).append("'>").append(wName).append("</div>");
        }
        template = template.replace("{{week_row}}", weekRow.toString());

        // 填充日期格子
        StringBuilder dayCells = new StringBuilder();
        int firstDayOffset = computeFirstDayOffset(year, month);
        for (int i = 0; i < firstDayOffset; i++) {
            dayCells.append("<div class='cal-day empty'></div>");
        }
        for (int day = 1; day <= daysInMonth; day++) {
            CalendarDate cellDate = new CalendarDate(year, month, day);
            boolean isToday = cellDate.equals(today);
            List<CalendarEvent> dayEvents = getEventsForDate(cellDate);
            boolean hasEv = !dayEvents.isEmpty();
            boolean isFuture = isFutureDate(cellDate, today);

            dayCells.append("<div id='day-").append(day).append("' class='cal-day");
            if (isToday) dayCells.append(" today");
            else if (isFuture) dayCells.append(" future");
            if (hasEv) dayCells.append(" has-event");
            if (dayEvents.size() >= 2) dayCells.append(" multi-event");
            dayCells.append("'>");

            dayCells.append("<div class='cal-day-head'>");
            dayCells.append("<div class='cal-day-num'>").append(day).append("</div>");
            if (hasEv) {
                dayCells.append("<div class='cal-day-dot'></div>");
            }
            dayCells.append("</div>");

            if (hasEv) {
                String firstName = dayEvents.get(0).getName();
                if (firstName.length() > 4) firstName = firstName.substring(0, 4);
                dayCells.append("<div class='cal-day-event'>").append(firstName).append("</div>");
                if (dayEvents.size() > 1) {
                    dayCells.append("<div class='cal-day-count'>+").append(dayEvents.size() - 1).append("</div>");
                }
            }
            dayCells.append("</div>");
        }
        template = template.replace("{{day_cells}}", dayCells.toString());

        // 填充事件内容
        List<CalendarEvent> todayEvents = getEventsForDate(today);
        StringBuilder eventsContent = new StringBuilder();
        if (todayEvents.isEmpty()) {
            eventsContent.append("<div class='cal-no-event'>\u2715 ")
                    .append(i18n("calendarmod.label.no_events")).append("</div>");
        } else {
            for (CalendarEvent ev : todayEvents) {
                int colorInt = ev.getColor();
                String colorCss = (colorInt == 0) ? "#fbbf24" : argbToCssHex(colorInt);
                eventsContent.append("<div class='cal-event-row' style='border-left-color:")
                        .append(colorCss).append("'>");
                eventsContent.append("<div class='cal-event-icon' style='color:")
                        .append(colorCss).append("'>\u25C6</div>");
                eventsContent.append("<div class='cal-event-content'>");
                eventsContent.append("<div class='cal-event-name'>").append(ev.getName()).append("</div>");
                String desc = ev.getDescription();
                if (desc != null && !desc.isEmpty()) {
                    eventsContent.append("<div class='cal-event-desc'>").append(desc).append("</div>");
                }
                eventsContent.append("</div>");
                if (ev.isFixed()) {
                    eventsContent.append("<div class='cal-event-tag'>\u21BB</div>");
                }
                eventsContent.append("</div>");
            }
        }
        template = template.replace("{{events_content}}", eventsContent.toString());

        return template;
    }

    private String argbToCssHex(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return String.format("#%02x%02x%02x", r, g, b);
    }

    @Override
    protected String getCss() {
        int screenW = Math.max(1, this.width);
        int screenH = Math.max(1, this.height);

        // 动态计算各区域尺寸，确保所有内容在屏幕内完整显示
        int rootPad = Math.max(6, screenH / 30);
        int gap = Math.max(4, screenH / 50);
        int headerPad = Math.max(6, screenH / 35);
        int navBtnSize = Math.max(28, screenH / 12);
        int titleFont = Math.max(14, screenH / 22);
        int eraFont = Math.max(9, screenH / 36);
        int subtitleFont = Math.max(9, screenH / 36);
        int weekCellPad = Math.max(3, screenH / 60);
        int weekFont = Math.max(10, screenH / 30);
        int dayGap = Math.max(3, screenH / 60);

        int btnHeight = Math.max(18, screenH / 16);
        int fixedH = rootPad * 2 + (navBtnSize + headerPad * 2) + gap * 4
                + (weekCellPad * 2 + weekFont + 2)
                + btnHeight + gap;
        int eventsPanelH = Math.max(50, screenH / 5);
        fixedH += eventsPanelH + gap;
        int dayH = Math.max(20, (screenH - fixedH) / 6);
        int dayPad = Math.max(2, dayH / 10);
        int dayNumFont = Math.max(11, dayH / 2);
        int dayEventFont = Math.max(8, dayH / 4);
        int btnWidth = Math.max(50, (screenW - rootPad * 2 - gap * 2) / 3);
        int btnFont = Math.max(9, screenH / 32);
        int eventRowPad = Math.max(3, eventsPanelH / 20);
        int eventFont = Math.max(10, eventsPanelH / 14);
        int eventDescFont = Math.max(8, eventsPanelH / 18);

        // 根据玩家选择加载对应样式的 CSS
        StyleManager.StyleInfo selectedStyle = StyleManager.getById(CalendarClientConfig.selectedStyle);
        String cssPath = StyleManager.getCssPath(selectedStyle);
        String css = loadTemplate(cssPath);

        // 兜底：样式 CSS 加载失败时使用内置默认
        if (css.isEmpty()) {
            css = loadTemplate(CSS_TEMPLATE);
        }
        // 最终兜底：全部加载失败则使用硬编码 fallback
        if (css.isEmpty()) {
            css = getFallbackCss(btnWidth, btnHeight);
        }

        // 替换所有动态尺寸变量
        css = css.replace("__ROOT_PAD__", String.valueOf(rootPad));
        css = css.replace("__GAP__", String.valueOf(gap));
        css = css.replace("__HEADER_PAD__", String.valueOf(headerPad));
        css = css.replace("__NAV_BTN__", String.valueOf(navBtnSize));
        css = css.replace("__TITLE_FONT__", String.valueOf(titleFont));
        css = css.replace("__ERA_FONT__", String.valueOf(eraFont));
        css = css.replace("__SUBTITLE_FONT__", String.valueOf(subtitleFont));
        css = css.replace("__WEEK_PAD__", String.valueOf(weekCellPad));
        css = css.replace("__WEEK_FONT__", String.valueOf(weekFont));
        css = css.replace("__DAY_GAP__", String.valueOf(dayGap));
        css = css.replace("__DAY_H__", String.valueOf(dayH));
        css = css.replace("__DAY_PAD__", String.valueOf(dayPad));
        css = css.replace("__DAY_NUM_FONT__", String.valueOf(dayNumFont));
        css = css.replace("__DAY_EVENT_FONT__", String.valueOf(dayEventFont));
        css = css.replace("__BTN__", String.valueOf(btnWidth));
        css = css.replace("__BTN_H__", String.valueOf(btnHeight));
        css = css.replace("__BTN_FONT__", String.valueOf(btnFont));
        css = css.replace("__EVENTS_H__", String.valueOf(eventsPanelH));
        css = css.replace("__EVENT_ROW_PAD__", String.valueOf(eventRowPad));
        css = css.replace("__EVENT_FONT__", String.valueOf(eventFont));
        css = css.replace("__EVENT_DESC_FONT__", String.valueOf(eventDescFont));

        return css;
    }

    /** 外部 CSS 加载失败时的兜底样式（灰白半透明现代化风格） */
    private String getFallbackCss(int btnWidth, int btnHeight) {
        return """
                .cal-root {
                    display: flex;
                    flex-direction: column;
                    padding: 18px;
                    background-color: #F8F8F8FA;
                    color: #2A2A35;
                    gap: 12px;
                    border-radius: 16px;
                }
                .cal-header {
                    display: flex;
                    align-items: center;
                    justify-content: space-between;
                    padding: 16px 20px;
                    background-color: #FFFFFFFF;
                    border-radius: 12px;
                    border-width: 1px;
                    border-color: #0000000A;
                }
                .cal-nav-btn {
                    width: 40px;
                    height: 40px;
                    background-color: #FFFFFFFF;
                    border-width: 1px;
                    border-color: #00000012;
                    border-radius: 10px;
                    color: #4A4A55;
                    font-size: 18px;
                    text-align: center;
                }
                .cal-title-wrap {
                    display: flex;
                    flex-direction: column;
                    align-items: center;
                }
                .cal-era { font-size: 12px; color: #8A8A95; }
                .cal-title { font-size: 22px; color: #1E1E28; }
                .cal-subtitle { font-size: 12px; color: #6A6A78; }
                .cal-week-row {
                    display: grid;
                    grid-template-columns: repeat(7, 1fr);
                    gap: 6px;
                }
                .cal-week-cell {
                    text-align: center;
                    padding: 10px 0;
                    font-size: 13px;
                    color: #6A6A78;
                    background-color: #F5F5F7CC;
                    border-radius: 8px;
                    border-width: 1px;
                    border-color: #00000008;
                }
                .cal-week-cell.sun { color: #D97757; }
                .cal-week-cell.sat { color: #5B8BD9; }
                .cal-grid {
                    display: grid;
                    grid-template-columns: repeat(7, 1fr);
                    gap: 6px;
                }
                .cal-day {
                    height: 78px;
                    padding: 8px;
                    background-color: #FFFFFFFF;
                    border-radius: 10px;
                    border-width: 1px;
                    border-color: #0000000A;
                }
                .cal-day.empty { background-color: transparent; border-color: transparent; }
                .cal-day.future { opacity: 0.6; }
                .cal-day.today {
                    border-width: 2px;
                    border-color: #5B8BD9B3;
                    background-color: #EEF4FFFF;
                }
                .cal-day.has-event {
                    border-color: #E6B45080;
                    background-color: #FFF9EEFF;
                }
                .cal-day.multi-event {
                    border-color: #52B78880;
                    background-color: #EFFBF5FF;
                }
                .cal-day-head {
                    display: flex;
                    justify-content: center;
                    align-items: center;
                    position: relative;
                }
                .cal-day-num {
                    font-size: 18px;
                    color: #3A3A48;
                    text-align: center;
                    width: 100%;
                }
                .cal-day.today .cal-day-num { color: #2563EB; }
                .cal-day-dot {
                    position: absolute;
                    top: 0;
                    right: 2px;
                    width: 6px;
                    height: 6px;
                    background-color: #E6B450;
                    border-radius: 3px;
                }
                .cal-day-event { font-size: 11px; color: #B45309; }
                .cal-day-count { font-size: 11px; color: #15803D; }
                .cal-events-panel {
                    padding: 14px 18px;
                    background-color: #FFFFFFFF;
                    border-radius: 12px;
                    border-width: 1px;
                    border-color: #0000000A;
                    max-height: 200px;
                    overflow-y: auto;
                }
                .cal-panel-header {
                    display: flex;
                    justify-content: space-between;
                    align-items: center;
                    margin-bottom: 10px;
                }
                .cal-panel-title { font-size: 14px; color: #4A4A58; }
                .cal-panel-stat { font-size: 12px; color: #8A8A95; }
                .cal-no-event { text-align: center; padding: 16px; font-size: 13px; color: #8A8A95; }
                .cal-event-row {
                    display: flex;
                    align-items: center;
                    padding: 8px 10px;
                    margin-bottom: 6px;
                    background-color: #F7F7F9FF;
                    border-radius: 8px;
                    border-width: 2px;
                    border-color: #E6B450;
                    gap: 8px;
                }
                .cal-event-icon { font-size: 14px; }
                .cal-event-content { display: flex; flex-direction: column; }
                .cal-event-name { font-size: 13px; color: #2A2A35; }
                .cal-event-desc { font-size: 11px; color: #6A6A78; }
                .cal-event-tag { font-size: 14px; color: #8A8A95; }
                .cal-bottom { display: flex; gap: 10px; justify-content: center; }
                .cal-btn {
                    width: __BTN__px;
                    height: __BTN_H__px;
                    padding: 0;
                    background-color: #FFFFFFFF;
                    border-width: 1px;
                    border-color: #00000014;
                    border-radius: 6px;
                    color: #3A3A48;
                    font-size: 12px;
                    text-align: center;
                    line-height: __BTN_H__px;
                }
                """.replace("__BTN__", String.valueOf(btnWidth))
                   .replace("__BTN_H__", String.valueOf(btnHeight));
    }

    public void handleClick(ClickEvent event) {
        String id = event.element().getId();
        if (id == null) return;
        if ("prev-month".equals(id)) {
            viewMonth--;
            if (viewMonth < 1) {
                viewMonth = getSystem().getMonthsPerYear();
                viewYear--;
            }
            if (viewYear < 1) {
                viewYear = 1;
                viewMonth = 1;
            }
            rebuild();
        } else if ("next-month".equals(id)) {
            viewMonth++;
            int monthsPerYear = getSystem().getMonthsPerYear();
            if (viewMonth > monthsPerYear) {
                viewMonth = 1;
                viewYear++;
            }
            rebuild();
        } else if ("open-client-config".equals(id)) {
            MinecraftClient.getInstance().setScreen(CalendarClientConfig.buildScreen(this));
        } else if ("open-calendar-config".equals(id)) {
            MinecraftClient.getInstance().setScreen(new CalendarConfigScreen());
        } else if ("open-events".equals(id)) {
            MinecraftClient.getInstance().setScreen(new CalendarEventScreen());
        }
    }
}
