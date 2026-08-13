package com.calendar.mod.client;

import com.calendar.mod.CalendarMod;
import com.calendar.mod.calendar.CalendarConfig;
import com.calendar.mod.calendar.CalendarDate;
import com.calendar.mod.network.CalendarClientCache;
import com.calendar.mod.network.CalendarConfigPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Language;

import java.util.ArrayList;
import java.util.List;

/**
 * 日历配置界面（原生 Screen + TextFieldWidget + ButtonWidget）。
 * 让玩家自定义历法参数，保存后通过 CalendarConfigPacket 同步到服务端。
 * 原理：init() 创建组件，render() 绘制标签，保存时解析输入并发包。
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

    private TextFieldWidget eraNameEdit;
    private TextFieldWidget monthsPerYearEdit;
    private TextFieldWidget yearOffsetEdit;
    private TextFieldWidget weekdayNamesEdit;
    private TextFieldWidget monthNamesEdit;
    private TextFieldWidget daysPerMonthEdit;

    public CalendarConfigScreen() {
        super(Text.translatable("calendarmod.screen.config_title"));
    }

    /** 本地化文本快捷方法 */
    private String i18n(String key) {
        return Language.getInstance().get(key);
    }

    @Override
    protected void init() {
        CalendarConfig cached = CalendarClientCache.INSTANCE.getConfig();
        int cachedYearOffset = CalendarClientCache.INSTANCE.getYearOffset();

        // 窗口大小变化时 init() 会重新调用，此处保留用户已输入的内容
        String eraName = eraNameEdit != null ? eraNameEdit.getText() : cached.getEraName();
        String monthsPerYear = monthsPerYearEdit != null ? monthsPerYearEdit.getText() : String.valueOf(cached.getMonthsPerYear());
        String yearOffset = yearOffsetEdit != null ? yearOffsetEdit.getText() : String.valueOf(cachedYearOffset);
        String weekdayNames = weekdayNamesEdit != null ? weekdayNamesEdit.getText() : String.join(",", cached.getWeekdayNames());
        String monthNames = monthNamesEdit != null ? monthNamesEdit.getText() : String.join(",", cached.getMonthNames());
        String daysPerMonth = daysPerMonthEdit != null ? daysPerMonthEdit.getText() : joinInts(cached.getDaysPerMonth());

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

        addDrawableChild(eraNameEdit);
        addDrawableChild(monthsPerYearEdit);
        addDrawableChild(yearOffsetEdit);
        addDrawableChild(weekdayNamesEdit);
        addDrawableChild(monthNamesEdit);
        addDrawableChild(daysPerMonthEdit);

        // 底部按钮：保存 / 返回
        int btnY = this.height - 30;
        addDrawableChild(ButtonWidget.builder(Text.translatable("calendarmod.button.save"),
                b -> onSave()).dimensions(centerX - 110, btnY, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("calendarmod.button.back"),
                b -> openMainScreen()).dimensions(centerX + 10, btnY, 100, 20).build());
    }

    /** 创建输入框并填入初始值 */
    private TextFieldWidget createEdit(String value, int x, int y) {
        TextFieldWidget box = new TextFieldWidget(this.textRenderer, x, y, EDIT_WIDTH, EDIT_HEIGHT, Text.empty());
        box.setMaxLength(256);
        box.setText(value);
        return box;
    }

    /** 解析所有输入，构建配置并发送到服务端 */
    private void onSave() {
        CalendarConfig cached = CalendarClientCache.INSTANCE.getConfig();
        CalendarConfig config = new CalendarConfig();
        config.setEraName(safeStr(eraNameEdit.getText(), cached.getEraName()));
        config.setMonthsPerYear(safeInt(monthsPerYearEdit.getText(), cached.getMonthsPerYear()));
        config.setWeekdayNames(splitList(weekdayNamesEdit.getText(), cached.getWeekdayNames()));
        config.setMonthNames(splitList(monthNamesEdit.getText(), cached.getMonthNames()));
        config.setDaysPerMonth(splitIntArray(daysPerMonthEdit.getText(), cached.getDaysPerMonth()));
        // startYear 不在此界面修改，沿用缓存值
        config.setStartYear(cached.getStartYear());

        int yearOffset = safeInt(yearOffsetEdit.getText(), CalendarClientCache.INSTANCE.getYearOffset());
        // dayZero 不修改，直接取缓存
        CalendarDate dayZero = CalendarClientCache.INSTANCE.getDayZero();

        CalendarConfigPacket packet = new CalendarConfigPacket(config, dayZero, yearOffset);
        PacketByteBuf buf = PacketByteBufs.create();
        CalendarConfigPacket.encode(packet, buf);
        ClientPlayNetworking.send(CalendarMod.CONFIG_ID, buf);
        openMainScreen();
    }

    /** 返回日历主界面（需重新绑定点击处理器） */
    private void openMainScreen() {
        CalendarScreen screen = new CalendarScreen();
        screen.setClickHandler(screen::handleClick);
        this.client.setScreen(screen);
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
    public void render(DrawContext drawContext, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(drawContext);

        int centerX = this.width / 2;
        // 标题居中
        drawContext.drawCenteredTextWithShadow(this.textRenderer, this.title, centerX, 18, 0xFFFFFF);

        int formWidth = LABEL_WIDTH + COL_GAP + EDIT_WIDTH;
        int labelX = centerX - formWidth / 2;
        int startY = 50;
        int color = 0xE0E0E0;

        // 行标签（垂直居中于输入框：输入框高 18，文字基线偏移 5）
        drawContext.drawTextWithShadow(this.textRenderer, i18n("calendarmod.label.era_name"), labelX, startY + 5, color);
        drawContext.drawTextWithShadow(this.textRenderer, i18n("calendarmod.label.months_per_year"), labelX, startY + ROW_HEIGHT + 5, color);
        drawContext.drawTextWithShadow(this.textRenderer, i18n("calendarmod.label.year_offset"), labelX, startY + ROW_HEIGHT * 2 + 5, color);
        drawContext.drawTextWithShadow(this.textRenderer, i18n("calendarmod.label.weekday_names"), labelX, startY + ROW_HEIGHT * 3 + 5, color);
        drawContext.drawTextWithShadow(this.textRenderer, i18n("calendarmod.label.month_names"), labelX, startY + ROW_HEIGHT * 4 + 5, color);
        drawContext.drawTextWithShadow(this.textRenderer, i18n("calendarmod.label.days_per_month"), labelX, startY + ROW_HEIGHT * 5 + 5, color);

        // 绘制所有组件
        super.render(drawContext, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
