package com.calendar.mod.client;

import com.calendar.mod.calendar.CalendarDate;
import com.calendar.mod.data.CalendarEvent;
import com.calendar.mod.network.CalendarClientCache;
import com.calendar.mod.network.CalendarEventPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 事件管理界面（原生 Screen + EditBox + Button + 列表颜色选择器）。
 * 上半部分为添加事件表单，中间为颜色选择列表，下半部分为已有事件列表。
 *
 * <p>26.2 适配要点：
 * <ul>
 *   <li>{@code DrawContext} → {@link GuiGraphicsExtractor}，render → extractRenderState</li>
 *   <li>{@code TextFieldWidget} → {@link EditBox}：setText→setValue，getText→getValue</li>
 *   <li>{@code ButtonWidget} → {@link Button}：builder.dimensions → builder.bounds</li>
 *   <li>{@code addDrawableChild} → {@code addRenderableWidget}</li>
 *   <li>{@code mouseClicked(double,int)} → {@code mouseClicked(MouseButtonEvent, boolean)}</li>
 *   <li>{@code mouseScrolled(double,double,double)} → {@code mouseScrolled(double,double,double,double)}（amount→scrollY）</li>
 *   <li>{@code Text} → {@link Component}，{@code Language.get} → {@code getOrDefault}</li>
 *   <li>{@code PacketByteBuf} → {@link FriendlyByteBuf}</li>
 *   <li>移除 {@code shouldPause}，{@code renderBackground} 由父类自动渲染</li>
 * </ul>
 */
public class CalendarEventScreen extends Screen {

    private static final int MAX_DISPLAY_EVENTS = 10;
    private static final int EDIT_HEIGHT = 18;
    private static final int LIST_ROW_HEIGHT = 18;
    private static final int COLOR_LIST_ROW_H = 16;
    private static final int COLOR_SWATCH_SIZE = 12;

    /** 预设颜色列表（显示名 → ARGB 值） */
    private static final ColorEntry[] PRESET_COLORS = {
            new ColorEntry("琥珀", 0xFFFFD700),
            new ColorEntry("珊瑚", 0xFFFF7F50),
            new ColorEntry("绯红", 0xFFDC143C),
            new ColorEntry("翠绿", 0xFF2E8B57),
            new ColorEntry("碧蓝", 0xFF1E90FF),
            new ColorEntry("紫罗兰", 0xFF8A2BE2),
            new ColorEntry("玫瑰", 0xFFC71585),
            new ColorEntry("金桔", 0xFFFF8C00),
            new ColorEntry("青竹", 0xFF20B2AA),
            new ColorEntry("靛青", 0xFF483D8B),
            new ColorEntry("银灰", 0xFF778899),
            new ColorEntry("墨黑", 0xFF2F2F2F),
    };

    // 布局常量
    private static final int TITLE_Y = 8;
    private static final int FORM_TITLE_Y = 26;
    private static final int DATE_Y = 46;
    private static final int NAME_Y = 68;
    private static final int DESC_Y = 90;
    private static final int COLOR_LABEL_Y = 112;
    private static final int COLOR_LIST_Y = 128;
    private static final int LIST_TITLE_Y = 210;
    private static final int LIST_START_Y = 222;

    private EditBox yearEdit;
    private EditBox monthEdit;
    private EditBox dayEdit;
    private EditBox nameEdit;
    private EditBox descEdit;

    /** 当前选中的颜色索引（对应 PRESET_COLORS） */
    private int selectedColorIndex = 0;

    /** 自定义颜色输入框 */
    private EditBox customColorEdit;
    private Button customToggleBtn;
    private boolean customColorMode = false;

    /** 颜色列表的滚动偏移 */
    private int colorScrollOffset = 0;
    private static final int COLOR_VISIBLE_COUNT = 4;

    /** 每年重复开关状态 */
    private boolean isFixed = false;
    private Button fixedBtn;

    /** 上次渲染时的事件数 */
    private int lastEventCount = -1;

    public CalendarEventScreen() {
        super(Component.translatable("calendarmod.screen.event_title"));
    }

    private String i18n(String key) {
        return Language.getInstance().getOrDefault(key);
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int formStartX = centerX - 150;

        // 保留输入
        String year = yearEdit != null ? yearEdit.getValue() : "1";
        String month = monthEdit != null ? monthEdit.getValue() : "1";
        String day = dayEdit != null ? dayEdit.getValue() : "1";
        String name = nameEdit != null ? nameEdit.getValue() : "";
        String desc = descEdit != null ? descEdit.getValue() : "";

        // 日期行
        yearEdit = createEdit(year, formStartX + 25, DATE_Y, 45);
        monthEdit = createEdit(month, formStartX + 105, DATE_Y, 45);
        dayEdit = createEdit(day, formStartX + 185, DATE_Y, 45);

        // 名称 / 描述
        nameEdit = createEdit(name, formStartX + 70, NAME_Y, 230);
        descEdit = createEdit(desc, formStartX + 70, DESC_Y, 230);

        addRenderableWidget(yearEdit);
        addRenderableWidget(monthEdit);
        addRenderableWidget(dayEdit);
        addRenderableWidget(nameEdit);
        addRenderableWidget(descEdit);

        // 颜色列表滚动按钮（放在列表框右侧）
        int listX = formStartX;
        int listRightX = listX + 120 + 4;
        addRenderableWidget(Button.builder(Component.literal("▲"), b -> scrollColorUp())
                .bounds(listRightX, COLOR_LIST_Y, 18, 12).build());
        addRenderableWidget(Button.builder(Component.literal("▼"), b -> scrollColorDown())
                .bounds(listRightX, COLOR_LIST_Y + COLOR_VISIBLE_COUNT * COLOR_LIST_ROW_H - 12, 18, 12).build());

        // 自定义颜色切换按钮 + 输入框（放在列表框右下方）
        int customX = listRightX + 24;
        customToggleBtn = Button.builder(Component.literal("自定义"), b -> toggleCustomColor())
                .bounds(customX, COLOR_LIST_Y, 52, 16).build();
        addRenderableWidget(customToggleBtn);

        String customVal = customColorEdit != null ? customColorEdit.getValue() : "#FFD700";
        customColorEdit = createEdit(customVal, customX, COLOR_LIST_Y + 20, 80);
        customColorEdit.setMaxLength(8);
        addRenderableWidget(customColorEdit);
        if (!customColorMode) {
            customColorEdit.visible = false;
            customToggleBtn.setMessage(Component.literal("自定义"));
        } else {
            customToggleBtn.setMessage(Component.literal("预设"));
        }

        // 每年重复 / 添加按钮（放在自定义输入框下方）
        int actionsY = COLOR_LIST_Y + COLOR_VISIBLE_COUNT * COLOR_LIST_ROW_H + 4;
        fixedBtn = Button.builder(fixedText(), b -> toggleFixed())
                .bounds(formStartX + 195, actionsY, 40, 20).build();
        addRenderableWidget(fixedBtn);
        addRenderableWidget(Button.builder(Component.translatable("calendarmod.button.add_event"),
                b -> onAdd()).bounds(formStartX + 245, actionsY, 55, 20).build());

        // 已有事件列表
        List<CalendarEvent> events = CalendarClientCache.INSTANCE.getEvents();
        int count = computeDisplayCount(events.size());
        for (int i = 0; i < count; i++) {
            final int idx = i;
            addRenderableWidget(Button.builder(Component.translatable("calendarmod.button.delete"),
                    b -> onDelete(idx)).bounds(formStartX + 240, LIST_START_Y + i * LIST_ROW_HEIGHT, 60, 16).build());
        }

        // 底部返回按钮
        addRenderableWidget(Button.builder(Component.translatable("calendarmod.button.back"),
                b -> openMainScreen()).bounds(centerX - 50, this.height - 22, 100, 20).build());

        lastEventCount = events.size();
    }

    private void scrollColorUp() {
        if (colorScrollOffset > 0) colorScrollOffset--;
    }

    private void scrollColorDown() {
        int maxOffset = Math.max(0, PRESET_COLORS.length - COLOR_VISIBLE_COUNT);
        if (colorScrollOffset < maxOffset) colorScrollOffset++;
    }

    private void selectColor(int index) {
        selectedColorIndex = index;
        customColorMode = false;
        if (customColorEdit != null) customColorEdit.visible = false;
        if (customToggleBtn != null) customToggleBtn.setMessage(Component.literal("自定义"));
    }

    private void toggleCustomColor() {
        customColorMode = !customColorMode;
        if (customColorEdit != null) customColorEdit.visible = customColorMode;
        if (customToggleBtn != null) {
            customToggleBtn.setMessage(Component.literal(customColorMode ? "预设" : "自定义"));
        }
    }

    private EditBox createEdit(String value, int x, int y, int width) {
        EditBox box = new EditBox(this.font, x, y, width, EDIT_HEIGHT, Component.empty());
        box.setMaxLength(128);
        box.setValue(value);
        return box;
    }

    private int computeDisplayCount(int total) {
        int backBtnY = this.height - 22;
        int available = backBtnY - 6 - LIST_START_Y;
        int maxFitting = available <= 0 ? 0 : available / LIST_ROW_HEIGHT;
        return Math.min(Math.min(total, MAX_DISPLAY_EVENTS), Math.max(0, maxFitting));
    }

    private Component fixedText() {
        return Component.literal(isFixed ? i18n("calendarmod.label.yes") : i18n("calendarmod.label.no"));
    }

    private void toggleFixed() {
        isFixed = !isFixed;
        if (fixedBtn != null) fixedBtn.setMessage(fixedText());
    }

    private int getSelectedColor() {
        if (customColorMode && customColorEdit != null) {
            return parseColor(customColorEdit.getValue(), 0xFFFFD700);
        }
        return PRESET_COLORS[selectedColorIndex].argb;
    }

    private void onAdd() {
        int year = safeInt(yearEdit.getValue(), 1);
        int month = clamp(safeInt(monthEdit.getValue(), 1), 1, 12);
        int day = Math.max(1, safeInt(dayEdit.getValue(), 1));
        String name = nameEdit.getValue();
        String desc = descEdit.getValue();
        int color = getSelectedColor();

        CalendarDate date;
        try {
            date = new CalendarDate(year, month, day);
        } catch (Exception e) {
            date = new CalendarDate(1, 1, 1);
        }
        CalendarEvent event = new CalendarEvent(date, name, desc, color, isFixed, new ArrayList<>());
        // 26.2：直接发送 payload 实例，无需手动构造 buf
        ClientPlayNetworking.send(new CalendarEventPacket(event));
    }

    private void onDelete(int index) {
        // 26.2：直接发送 payload 实例，无需手动构造 buf
        ClientPlayNetworking.send(new CalendarEventPacket(index));
    }

    private void openMainScreen() {
        CalendarScreen screen = new CalendarScreen();
        screen.setClickHandler(screen::handleClick);
        this.minecraft.gui.setScreen(screen);
    }

    private int safeInt(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private int parseColor(String s, int def) {
        try {
            String t = s.trim();
            if (t.startsWith("#")) t = t.substring(1);
            int rgb = Integer.parseInt(t, 16);
            return 0xFF000000 | (rgb & 0xFFFFFF);
        } catch (Exception e) {
            return def;
        }
    }

    /**
     * 26.2：mouseClicked 签名改为 {@code (MouseButtonEvent, boolean)}。
     * 从 event 中提取 x/y/button 后沿用原逻辑。
     */
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mouseX = (int) event.x();
        int mouseY = (int) event.y();

        int centerX = this.width / 2;
        int formStartX = centerX - 150;

        // 颜色列表区域点击检测
        if (mouseX >= formStartX && mouseX <= formStartX + 120
                && mouseY >= COLOR_LIST_Y && mouseY < COLOR_LIST_Y + COLOR_VISIBLE_COUNT * COLOR_LIST_ROW_H) {
            int relativeY = mouseY - COLOR_LIST_Y;
            int clickedRow = relativeY / COLOR_LIST_ROW_H;
            int dataIndex = colorScrollOffset + clickedRow;
            if (dataIndex >= 0 && dataIndex < PRESET_COLORS.length) {
                selectColor(dataIndex);
                return true;
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    /**
     * 26.2：mouseScrolled 签名增加 scrollX 参数，原 amount 对应 scrollY。
     */
    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        int centerX = this.width / 2;
        int formStartX = centerX - 150;

        // 颜色列表区域滚轮
        if (x >= formStartX && x <= formStartX + 120
                && y >= COLOR_LIST_Y - 4 && y < COLOR_LIST_Y + COLOR_VISIBLE_COUNT * COLOR_LIST_ROW_H + 4) {
            if (scrollY > 0) scrollColorUp();
            else if (scrollY < 0) scrollColorDown();
            return true;
        }

        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int currentCount = CalendarClientCache.INSTANCE.getEvents().size();
        if (currentCount != lastEventCount) {
            lastEventCount = currentCount;
            rebuildWidgets();
        }

        // 26.2：背景由父类自动渲染，不显式调用 renderBackground
        int centerX = this.width / 2;
        int formStartX = centerX - 150;
        int color = 0xFFE0E0E0;

        // 标题居中
        // 26.2：text() 颜色为 ARGB 格式，必须带 0xFF alpha 前缀，否则 alpha=0 文字透明不可见
        String titleStr = this.title.getString();
        graphics.text(this.font, titleStr, centerX - this.font.width(titleStr) / 2, TITLE_Y, 0xFFFFFFFF, true);
        graphics.text(this.font, i18n("calendarmod.button.add_event"), formStartX, FORM_TITLE_Y, 0xFFFFD700, true);

        graphics.text(this.font, i18n("calendarmod.label.year"), formStartX, DATE_Y + 5, color, true);
        graphics.text(this.font, i18n("calendarmod.label.month"), formStartX + 80, DATE_Y + 5, color, true);
        graphics.text(this.font, i18n("calendarmod.label.day"), formStartX + 160, DATE_Y + 5, color, true);

        graphics.text(this.font, i18n("calendarmod.label.event_name"), formStartX, NAME_Y + 5, color, true);
        graphics.text(this.font, i18n("calendarmod.label.event_desc"), formStartX, DESC_Y + 5, color, true);

        // 颜色标签 + 当前选中颜色显示
        graphics.text(this.font, i18n("calendarmod.label.event_color"), formStartX, COLOR_LABEL_Y + 5, color, true);
        if (!customColorMode) {
            ColorEntry ce = PRESET_COLORS[selectedColorIndex];
            graphics.fill(formStartX + 55, COLOR_LABEL_Y + 2, formStartX + 55 + 10, COLOR_LABEL_Y + 12, ce.argb);
            graphics.text(this.font, ce.name + " " + String.format("#%06X", ce.argb & 0xFFFFFF), formStartX + 70, COLOR_LABEL_Y + 5, 0xFFCCCCCC, true);
        } else {
            graphics.text(this.font, i18n("calendarmod.label.event_color") + ": " + customColorEdit.getValue(), formStartX + 180, COLOR_LABEL_Y + 5, 0xFFCCCCCC, true);
        }

        // 绘制颜色列表框背景
        int listX = formStartX;
        int listY = COLOR_LIST_Y;
        int listW = 120;
        int listH = COLOR_VISIBLE_COUNT * COLOR_LIST_ROW_H;
        graphics.fill(listX, listY, listX + listW, listY + listH, 0xCC1A1A1A);
        graphics.fill(listX, listY, listX + listW, listY + 1, 0xFF555555);
        graphics.fill(listX, listY + listH - 1, listX + listW, listY + listH, 0xFF555555);
        graphics.fill(listX, listY, listX + 1, listY + listH, 0xFF555555);
        graphics.fill(listX + listW - 1, listY, listX + listW, listY + listH, 0xFF555555);

        // 绘制可见的颜色列表行
        for (int row = 0; row < COLOR_VISIBLE_COUNT; row++) {
            int idx = colorScrollOffset + row;
            if (idx >= PRESET_COLORS.length) break;

            int rowY = listY + row * COLOR_LIST_ROW_H;
            ColorEntry ce = PRESET_COLORS[idx];

            boolean selected = !customColorMode && (idx == selectedColorIndex);

            // 选中行背景
            if (selected) {
                graphics.fill(listX + 1, rowY, listX + listW - 1, rowY + COLOR_LIST_ROW_H, 0xFF3A3A50);
                graphics.fill(listX + 1, rowY, listX + listW - 1, rowY + 1, 0xFF5A5A80);
            } else {
                graphics.fill(listX + 1, rowY, listX + listW - 1, rowY + COLOR_LIST_ROW_H, row % 2 == 0 ? 0xCC252530 : 0xCC2D2D38);
            }

            // 颜色色块
            int swatchX = listX + 4;
            int swatchY = rowY + (COLOR_LIST_ROW_H - COLOR_SWATCH_SIZE) / 2;
            graphics.fill(swatchX, swatchY, swatchX + COLOR_SWATCH_SIZE, swatchY + COLOR_SWATCH_SIZE, ce.argb);
            // 色块边框
            graphics.fill(swatchX, swatchY, swatchX + COLOR_SWATCH_SIZE, swatchY + 1, 0xFFFFFFFF);
            graphics.fill(swatchX, swatchY + COLOR_SWATCH_SIZE - 1, swatchX + COLOR_SWATCH_SIZE, swatchY + COLOR_SWATCH_SIZE, 0xFFFFFFFF);
            graphics.fill(swatchX, swatchY, swatchX + 1, swatchY + COLOR_SWATCH_SIZE, 0xFFFFFFFF);
            graphics.fill(swatchX + COLOR_SWATCH_SIZE - 1, swatchY, swatchX + COLOR_SWATCH_SIZE, swatchY + COLOR_SWATCH_SIZE, 0xFFFFFFFF);

            // 颜色名称
            graphics.text(this.font, ce.name, swatchX + COLOR_SWATCH_SIZE + 4, rowY + 4, selected ? 0xFFFFFFFF : 0xFFD0D0D0, true);

            // 颜色十六进制值
            graphics.text(this.font, String.format("#%06X", ce.argb & 0xFFFFFF), listX + listW - 45, rowY + 4, selected ? 0xFFCCCCCC : 0xFF8A8A8A, true);

            // 鼠标悬停提示
            if (mouseX >= listX && mouseX <= listX + listW && mouseY >= rowY && mouseY < rowY + COLOR_LIST_ROW_H) {
                graphics.text(this.font, ce.name + " " + String.format("#%06X", ce.argb & 0xFFFFFF), mouseX + 10, mouseY - 12, 0xFFFFFFFF, true);
            }
        }

        int actionsY = COLOR_LIST_Y + COLOR_VISIBLE_COUNT * COLOR_LIST_ROW_H + 4;
        graphics.text(this.font, i18n("calendarmod.label.event_fixed"), formStartX + 195, actionsY + 5, color, true);

        // 事件列表
        graphics.text(this.font, i18n("calendarmod.label.event_list"), formStartX, LIST_TITLE_Y, 0xFFFFD700, true);
        List<CalendarEvent> events = CalendarClientCache.INSTANCE.getEvents();
        int count = computeDisplayCount(events.size());
        for (int i = 0; i < count; i++) {
            CalendarEvent ev = events.get(i);
            int rowY = LIST_START_Y + i * LIST_ROW_HEIGHT;
            int textCol = ev.getColor() == 0 ? 0xFFFFFFFF : ev.getColor();
            graphics.text(this.font, formatEvent(ev), formStartX, rowY + 5, textCol, true);
        }
        if (events.size() > count) {
            graphics.text(this.font, "(" + events.size() + ")", formStartX, LIST_START_Y + count * LIST_ROW_HEIGHT + 4, 0xFFAAAAAA, true);
        }

        // 绘制所有 addRenderableWidget 注册的组件（输入框、按钮）
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private String formatEvent(CalendarEvent ev) {
        CalendarDate d = ev.getDate();
        String dateStr = (d == null) ? "??" : (d.getYear() + "/" + d.getMonth() + "/" + d.getDay());
        String repeat = ev.isFixed() ? i18n("calendarmod.label.yes") : i18n("calendarmod.label.no");
        return dateStr + " " + ev.getName() + " [" + repeat + "]";
    }

    /** 预设颜色条目 */
    private record ColorEntry(String name, int argb) {}
}
