package com.calendar.mod.client;

import com.calendar.mod.CalendarMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 客户端配置管理（Cloth Config + JSON 文件持久化）。
 * 所有字段使用 volatile 保证多线程可见性。
 * 配置文件存储在 Minecraft config 目录下的 calendarmod-client.json。
 */
public class CalendarClientConfig {

    private CalendarClientConfig() {}

    private static final String CONFIG_FILE = "calendarmod-client.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // ===== HUD 配置 =====
    public static volatile boolean hudEnabled = true;
    public static volatile int hudX = 10;
    public static volatile int hudY = 10;
    public static volatile boolean hudRightAlign = true;
    public static volatile boolean showTodayEvents = true;
    public static volatile boolean showEventDescriptions = false;

    // ===== 日历界面配置 =====
    public static volatile int defaultMonthView = 0;
    public static volatile boolean autoOpenCalendarOnLogin = false;
    public static volatile String selectedStyle = "default";

    /** 配置数据快照（用于序列化） */
    private static class ConfigData {
        boolean hudEnabled = true;
        int hudX = 10;
        int hudY = 10;
        boolean hudRightAlign = true;
        boolean showTodayEvents = true;
        boolean showEventDescriptions = false;
        int defaultMonthView = 0;
        boolean autoOpenCalendarOnLogin = false;
        String selectedStyle = "default";
    }

    /** 从 JSON 文件加载配置 */
    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE);
        if (!Files.exists(path)) {
            save(); // 首次启动写入默认值
            return;
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            ConfigData data = GSON.fromJson(reader, ConfigData.class);
            if (data == null) return;
            hudEnabled = data.hudEnabled;
            hudX = data.hudX;
            hudY = data.hudY;
            hudRightAlign = data.hudRightAlign;
            showTodayEvents = data.showTodayEvents;
            showEventDescriptions = data.showEventDescriptions;
            defaultMonthView = data.defaultMonthView;
            autoOpenCalendarOnLogin = data.autoOpenCalendarOnLogin;
            // 兼容旧配置：selectedStyle 为 null 或空字符串 -> 使用默认
            selectedStyle = (data.selectedStyle == null || data.selectedStyle.isEmpty())
                    ? "default" : data.selectedStyle;
            // 验证 selectedStyle 是否为有效样式 id，不在列表中则回退 default
            validateSelectedStyle();
        } catch (IOException e) {
            CalendarMod.LOGGER.error("加载客户端配置失败", e);
        }
    }

    /** 校验 selectedStyle 必须在当前可用样式列表中存在，否则回退到 "default" */
    private static void validateSelectedStyle() {
        // 如果 StyleManager 已刷新过，则校验
        try {
            List<StyleManager.StyleInfo> styles = StyleManager.getStyles();
            boolean valid = false;
            for (StyleManager.StyleInfo s : styles) {
                if (s.id.equals(selectedStyle)) {
                    valid = true;
                    break;
                }
            }
            if (!valid) {
                CalendarMod.LOGGER.warn("配置中的样式 id '{}' 不存在，回退到 default", selectedStyle);
                selectedStyle = "default";
                save();
            }
        } catch (Exception ignored) {
            // 样式管理器不可用时不做处理（如 ResourceManager 未就绪）
        }
    }

    /** 保存配置到 JSON 文件 */
    public static void save() {
        ConfigData data = new ConfigData();
        data.hudEnabled = hudEnabled;
        data.hudX = hudX;
        data.hudY = hudY;
        data.hudRightAlign = hudRightAlign;
        data.showTodayEvents = showTodayEvents;
        data.showEventDescriptions = showEventDescriptions;
        data.defaultMonthView = defaultMonthView;
        data.autoOpenCalendarOnLogin = autoOpenCalendarOnLogin;
        data.selectedStyle = selectedStyle;

        Path path = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE);
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            CalendarMod.LOGGER.error("保存客户端配置失败", e);
        }
    }

    /** 构建 Cloth Config 配置屏幕 */
    public static Screen buildScreen(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("calendarmod.config.title"));

        ConfigEntryBuilder e = builder.entryBuilder();

        // === HUD 设置分类 ===
        ConfigCategory hudCat = builder.getOrCreateCategory(
                Component.translatable("calendarmod.config.category.hud"));

        hudCat.addEntry(e.startBooleanToggle(
                Component.translatable("calendarmod.config.hud_enabled"),
                hudEnabled)
                .setDefaultValue(true)
                .setSaveConsumer(v -> { hudEnabled = v; save(); })
                .setTooltip(Component.translatable("calendarmod.config.hud_enabled.tooltip"))
                .build());

        hudCat.addEntry(e.startBooleanToggle(
                Component.translatable("calendarmod.config.hud_right_align"),
                hudRightAlign)
                .setDefaultValue(true)
                .setSaveConsumer(v -> { hudRightAlign = v; save(); })
                .setTooltip(Component.translatable("calendarmod.config.hud_right_align.tooltip"))
                .build());

        hudCat.addEntry(e.startIntSlider(
                Component.translatable("calendarmod.config.hud_x"),
                hudX, 0, 500)
                .setDefaultValue(10)
                .setSaveConsumer(v -> { hudX = v; save(); })
                .setTooltip(Component.translatable("calendarmod.config.hud_x.tooltip"))
                .build());

        hudCat.addEntry(e.startIntSlider(
                Component.translatable("calendarmod.config.hud_y"),
                hudY, 0, 500)
                .setDefaultValue(10)
                .setSaveConsumer(v -> { hudY = v; save(); })
                .setTooltip(Component.translatable("calendarmod.config.hud_y.tooltip"))
                .build());

        hudCat.addEntry(e.startBooleanToggle(
                Component.translatable("calendarmod.config.show_today_events"),
                showTodayEvents)
                .setDefaultValue(true)
                .setSaveConsumer(v -> { showTodayEvents = v; save(); })
                .build());

        hudCat.addEntry(e.startBooleanToggle(
                Component.translatable("calendarmod.config.show_event_descs"),
                showEventDescriptions)
                .setDefaultValue(false)
                .setSaveConsumer(v -> { showEventDescriptions = v; save(); })
                .build());

        // === 日历界面设置分类 ===
        ConfigCategory screenCat = builder.getOrCreateCategory(
                Component.translatable("calendarmod.config.category.screen"));

        screenCat.addEntry(e.startIntSlider(
                Component.translatable("calendarmod.config.default_month_view"),
                defaultMonthView, -12, 12)
                .setDefaultValue(0)
                .setSaveConsumer(v -> { defaultMonthView = v; save(); })
                .build());

        screenCat.addEntry(e.startBooleanToggle(
                Component.translatable("calendarmod.config.auto_open_login"),
                autoOpenCalendarOnLogin)
                .setDefaultValue(false)
                .setSaveConsumer(v -> { autoOpenCalendarOnLogin = v; save(); })
                .build());

        // 样式选择下拉框
        {
            StyleManager.refreshStyles();
            List<StyleManager.StyleInfo> styles = StyleManager.getStyles();
            String[] ids = styles.stream().map(s -> s.id).toArray(String[]::new);

            // 校验 currentId 是否在 ids 中，否则回退到第一个可用 id
            String currentId = selectedStyle;
            if (currentId == null) currentId = "default";
            boolean found = false;
            for (String id : ids) {
                if (id.equals(currentId)) { found = true; break; }
            }
            if (!found && ids.length > 0) {
                currentId = ids[0];
                selectedStyle = currentId;
            }
            final String displayId = currentId;

            screenCat.addEntry(e.<String>startSelector(
                    Component.translatable("calendarmod.config.style"),
                    ids,
                    displayId)
                    .setNameProvider(id -> {
                        for (StyleManager.StyleInfo s : styles) {
                            if (s.id.equals(id)) return Component.literal(s.name);
                        }
                        return Component.literal(id);
                    })
                    .setDefaultValue("default")
                    .setSaveConsumer(id -> {
                        selectedStyle = id;
                        save();
                    })
                    .setTooltip(Component.translatable("calendarmod.config.style.tooltip"))
                    .build());
        }

        return builder.build();
    }
}