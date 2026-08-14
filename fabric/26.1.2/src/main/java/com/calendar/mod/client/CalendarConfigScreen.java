package com.calendar.mod.client;

import com.calendar.mod.calendar.CalendarConfig;
import com.calendar.mod.calendar.CalendarDate;
import com.calendar.mod.network.CalendarClientCache;
import com.calendar.mod.network.CalendarConfigPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 日历配置界面（原生 Screen + EditBox + Button）。
 * 让玩家自定义历法参数，保存后通过 CalendarConfigPacket 同步到服务端。
 * 原理：init() 创建组件，extractRenderState() 绘制标签，保存时解析输入并发包。
 *
 * <p>26.2 适配要点：
 * <ul>
 *   <li>{@code Screen} 包名 gui.screen → gui.screens</li>
 *   <li>{@code DrawContext} → {@link GuiGraphicsExtractor}，render → extractRenderState</li>
 *   <li>{@code TextFieldWidget} → {@link EditBox}：setText→setValue，getText→getValue</li>
 *   <li>{@code ButtonWidget} → {@link Button}：builder.dimensions → builder.bounds</li>
 *   <li>{@code addDrawableChild} → {@code addRenderableWidget}</li>
 *   <li>{@code Text} → {@link Component}，{@code Language.get} → {@code getOrDefault}</li>
 *   <li>移除 {@code shouldPause}（26.2 已删除该方法）</li>
 *   <li>{@code this.textRenderer} → {@code this.font}，{@code this.client} → {@code this.minecraft}</li>
 *   <li>{@code renderBackground} 由父类自动渲染，不再显式调用</li>
 * </ul>
 */
public class CalendarConfigScreen extends Screen {

    /** 标签列宽 */
    private static final int LABEL_WIDTH = 110;
    /** 输入框统一宽度 */
    private static final int EDIT_WIDTH = 180;
    /** 输入框高度 */
    private static final int EDIT_HEIGHT = 18;
    /** 行间距 */
    private static final int ROW_HEIGHT = 24;
    /** 标签与输入框之间的间隔 */
    private static final int COL_GAP = 8;

    private EditBox eraNameEdit;
    private EditBox monthsPerYearEdit;
    private EditBox yearOffsetEdit;
    private EditBox weekdayNamesEdit;
    private EditBox monthNamesEdit;
    private EditBox daysPerMonthEdit;

    public CalendarConfigScreen() {
        super(Component.translatable("calendarmod.screen.config_title"));
    }

    /** 本地化文本快捷方法 */
    private String i18n(String key) {
        return Language.getInstance().getOrDefault(key);
    }

    @Override
    protected void init() {
        CalendarConfig cached = CalendarClientCache.INSTANCE.getConfig();
        int cachedYearOffset = CalendarClientCache.INSTANCE.getYearOffset();

        // 窗口大小变化时 init() 会重新调用，此处保留用户已输入的内容
        String eraName = eraNameEdit != null ? eraNameEdit.getValue() : cached.getEraName();
        String monthsPerYear = monthsPerYearEdit != null ? monthsPerYearEdit.getValue() : String.valueOf(cached.getMonthsPerYear());
        String yearOffset = yearOffsetEdit != null ? yearOffsetEdit.getValue() : String.valueOf(cachedYearOffset);
        String weekdayNames = weekdayNamesEdit != null ? weekdayNamesEdit.getValue() : String.join(",", cached.getWeekdayNames());
        String monthNames = monthNamesEdit != null ? monthNamesEdit.getValue() : String.join(",", cached.getMonthNames());
        String daysPerMonth = daysPerMonthEdit != null ? daysPerMonthEdit.getValue() : joinInts(cached.getDaysPerMonth());

        int centerX = this.width / 2;
        int formWidth = LABEL_WIDTH + COL_GAP + EDIT_WIDTH;
        int startX = centerX - formWidth / 2;
        int editX = startX + LABEL_WIDTH + COL_GAP;
        int startY = 50;

        eraNameEdit = createEdit(eraName, editX, startY);
        monthsPerYearEdit = createEdit(monthsPerYear, editX, startY + ROW_HEIGHT);
        yearOffsetEdit = createEdit(yearOffset, editX, startY + ROW_HEIGHT * 2);
        weekdayNamesEdit = createEdit(weekdayNames, editX, startY + ROW_HEIGHT * 3);
        monthNamesEdit = createEdit(monthNames, editX, startY + ROW_HEIGHT * 4);
        daysPerMonthEdit = createEdit(daysPerMonth, editX, startY + ROW_HEIGHT * 5);

        addRenderableWidget(eraNameEdit);
        addRenderableWidget(monthsPerYearEdit);
        addRenderableWidget(yearOffsetEdit);
        addRenderableWidget(weekdayNamesEdit);
        addRenderableWidget(monthNamesEdit);
        addRenderableWidget(daysPerMonthEdit);

        // 底部按钮：保存 / 返回
        int btnY = this.height - 30;
        addRenderableWidget(Button.builder(Component.translatable("calendarmod.button.save"),
                b -> onSave()).bounds(centerX - 110, btnY, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("calendarmod.button.back"),
                b -> openMainScreen()).bounds(centerX + 10, btnY, 100, 20).build());
    }

    /** 创建输入框并填入初始值 */
    private EditBox createEdit(String value, int x, int y) {
        EditBox box = new EditBox(this.font, x, y, EDIT_WIDTH, EDIT_HEIGHT, Component.empty());
        box.setMaxLength(256);
        box.setValue(value);
        return box;
    }

    /** 解析所有输入，构建配置并发送到服务端 */
    private void onSave() {
        CalendarConfig cached = CalendarClientCache.INSTANCE.getConfig();
        CalendarConfig config = new CalendarConfig();
        config.setEraName(safeStr(eraNameEdit.getValue(), cached.getEraName()));
        config.setMonthsPerYear(safeInt(monthsPerYearEdit.getValue(), cached.getMonthsPerYear()));
        config.setWeekdayNames(splitList(weekdayNamesEdit.getValue(), cached.getWeekdayNames()));
        config.setMonthNames(splitList(monthNamesEdit.getValue(), cached.getMonthNames()));
        config.setDaysPerMonth(splitIntArray(daysPerMonthEdit.getValue(), cached.getDaysPerMonth()));
        // startYear 不在此界面修改，沿用缓存值
        config.setStartYear(cached.getStartYear());

        int yearOffset = safeInt(yearOffsetEdit.getValue(), CalendarClientCache.INSTANCE.getYearOffset());
        // dayZero 不修改，直接取缓存
        CalendarDate dayZero = CalendarClientCache.INSTANCE.getDayZero();

        // 26.2：直接发送 payload 实例，无需手动构造 buf
        CalendarConfigPacket payload = new CalendarConfigPacket(config, dayZero, yearOffset);
        ClientPlayNetworking.send(payload);
        openMainScreen();
    }

    /** 返回日历主界面（需重新绑定点击处理器） */
    private void openMainScreen() {
        CalendarScreen screen = new CalendarScreen();
        screen.setClickHandler(screen::handleClick);
        this.minecraft.setScreen(screen);
    }

    // ===== 输入解析工具（失败回退默认值，避免异常导致崩溃） =====

    private String safeStr(String s, String def) {
        return (s == null || s.isEmpty()) ? def : s;
    }

    private int safeInt(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    /** 按逗号分割为字符串列表，失败回退默认值 */
    private List<String> splitList(String s, List<String> def) {
        if (s == null || s.isBlank()) return new ArrayList<>(def);
        try {
            String[] parts = s.split(",");
            List<String> list = new ArrayList<>();
            for (String p : parts) {
                String t = p.trim();
                if (!t.isEmpty()) list.add(t);
            }
            return list.isEmpty() ? new ArrayList<>(def) : list;
        } catch (Exception e) {
            return new ArrayList<>(def);
        }
    }

    /** 按逗号分割为 int 数组，失败回退默认值 */
    private int[] splitIntArray(String s, int[] def) {
        if (s == null || s.isBlank()) return def;
        try {
            String[] parts = s.split(",");
            int[] arr = new int[parts.length];
            for (int i = 0; i < parts.length; i++) {
                arr[i] = Math.max(1, Integer.parseInt(parts[i].trim()));
            }
            return arr.length == 0 ? def : arr;
        } catch (Exception e) {
            return def;
        }
    }

    /** int 数组按逗号拼接为字符串 */
    private String joinInts(int[] arr) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(arr[i]);
        }
        return sb.toString();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // 26.2：背景由父类自动渲染，先画标签再调 super 画组件（与原 render 顺序一致）
        int centerX = this.width / 2;
        // 标题居中（GuiGraphicsExtractor 无 drawCenteredTextWithShadow，用 text + 手动算宽度）
        // 26.1.2：text() 颜色为 ARGB 格式，必须带 0xFF alpha 前缀，否则 alpha=0 文字透明不可见
        String titleStr = this.title.getString();
        graphics.text(this.font, titleStr, centerX - this.font.width(titleStr) / 2, 18, 0xFFFFFFFF, true);

        int formWidth = LABEL_WIDTH + COL_GAP + EDIT_WIDTH;
        int labelX = centerX - formWidth / 2;
        int startY = 50;
        int color = 0xFFE0E0E0;

        // 行标签（垂直居中于输入框：输入框高 18，文字基线偏移 5）
        graphics.text(this.font, i18n("calendarmod.label.era_name"), labelX, startY + 5, color, true);
        graphics.text(this.font, i18n("calendarmod.label.months_per_year"), labelX, startY + ROW_HEIGHT + 5, color, true);
        graphics.text(this.font, i18n("calendarmod.label.year_offset"), labelX, startY + ROW_HEIGHT * 2 + 5, color, true);
        graphics.text(this.font, i18n("calendarmod.label.weekday_names"), labelX, startY + ROW_HEIGHT * 3 + 5, color, true);
        graphics.text(this.font, i18n("calendarmod.label.month_names"), labelX, startY + ROW_HEIGHT * 4 + 5, color, true);
        graphics.text(this.font, i18n("calendarmod.label.days_per_month"), labelX, startY + ROW_HEIGHT * 5 + 5, color, true);

        // 绘制所有 addRenderableWidget 注册的组件（输入框、按钮）
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }
}
