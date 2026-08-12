package com.calendar.mod.client;

import com.calendar.mod.CalendarMod;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 样式管理器：动态扫描模组内置 + 资源包提供的样式。
 * 玩家可在 Cloth Config 中选择样式，样式列表自动识别新增。
 *
 * 工作原理：
 * 1. 每次打开配置界面时调用 {@link #refreshStyles()} 重新扫描
 * 2. 优先从 ResourceManager 读取 styles.json（资源包优先）
 * 3. 再从 ClassLoader 回退读取（模组自带）
 * 4. 遍历 styles.json 中列出的每个样式文件，验证存在性
 * 5. 始终保证至少有默认样式可用
 */
public class StyleManager {

    private static final String STYLES_META_PATH = "assets/calendarmod/templates/styles.json";
    private static final String BASE_CSS_DIR = "assets/calendarmod/templates/";

    private static final List<StyleInfo> STYLES = new ArrayList<>();
    private static long lastRefreshTime = 0;

    /** HUD 颜色配置（ARGB 格式：0xAARRGGBB） */
    public static class HudColors {
        /** 阴影颜色（多层外扩） */
        public final int shadow;
        /** 主体背景色 */
        public final int body;
        /** 顶部装饰条颜色 */
        public final int decor;
        /** 边框颜色 */
        public final int border;
        /** 日期/主文字颜色 */
        public final int textPrimary;
        /** 次要文字颜色 */
        public final int textSecondary;
        /** 事件文字颜色 */
        public final int textEvent;

        public HudColors(int shadow, int body, int decor, int border,
                         int textPrimary, int textSecondary, int textEvent) {
            this.shadow = shadow;
            this.body = body;
            this.decor = decor;
            this.border = border;
            this.textPrimary = textPrimary;
            this.textSecondary = textSecondary;
            this.textEvent = textEvent;
        }
    }

    /** 样式信息 */
    public static class StyleInfo {
        public final String id;
        public final String name;
        public final String description;
        /** 相对 templates/ 的路径，如 "calendar_screen.css" 或 "styles/dark.css" */
        public final String cssFile;
        public final boolean builtin;
        /** HUD 颜色配置 */
        public final HudColors hudColors;

        public StyleInfo(String id, String name, String description, String cssFile,
                         boolean builtin, HudColors hudColors) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.cssFile = cssFile;
            this.builtin = builtin;
            this.hudColors = hudColors != null ? hudColors : defaultHudColors();
        }

        /** 默认 HUD 配色（灰白半透明） */
        private static HudColors defaultHudColors() {
            return new HudColors(
                    0x28000000,   // shadow
                    0xE8F7F7F8,   // body
                    0xFFE4E7EC,   // decor
                    0x1A000000,   // border
                    0xFF1E293B,   // textPrimary
                    0xFF475569,   // textSecondary
                    0xFFB45309    // textEvent
            );
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private StyleManager() {}

    /**
     * 刷新样式列表。每次打开配置界面时调用。
     * 会自动检测资源包中新增的样式。
     * 始终保证至少有默认样式可用。
     */
    public static void refreshStyles() {
        STYLES.clear();
        loadStylesFromJson();

        // 确保至少有默认样式
        if (STYLES.isEmpty()) {
            HudColors defHud = new HudColors(
                    0x28000000, 0xE8F7F7F8, 0xFFE4E7EC, 0x1A000000,
                    0xFF1E293B, 0xFF475569, 0xFFB45309);
            STYLES.add(new StyleInfo("default", "灰白(默认)", "默认灰白色调",
                    "calendar_screen.css", true, defHud));
        }
        lastRefreshTime = System.currentTimeMillis();
    }

    /** 获取所有可用样式（先刷新再返回） */
    public static List<StyleInfo> getStyles() {
        if (System.currentTimeMillis() - lastRefreshTime > 30000) {
            refreshStyles();
        }
        if (STYLES.isEmpty()) {
            refreshStyles();
        }
        return Collections.unmodifiableList(new ArrayList<>(STYLES));
    }

    /** 根据 id 查找样式，找不到返回默认样式 */
    public static StyleInfo getById(String id) {
        for (StyleInfo s : STYLES) {
            if (s.id.equals(id)) return s;
        }
        return getDefault();
    }

    /** 获取默认样式（第一个或 id=default） */
    public static StyleInfo getDefault() {
        if (!STYLES.isEmpty()) {
            for (StyleInfo s : STYLES) {
                if ("default".equals(s.id)) return s;
            }
            return STYLES.get(0);
        }
        HudColors defHud = new HudColors(
                0x28000000, 0xE8F7F7F8, 0xFFE4E7EC, 0x1A000000,
                0xFF1E293B, 0xFF475569, 0xFFB45309);
        return new StyleInfo("default", "灰白(默认)", "默认灰白色调",
                "calendar_screen.css", true, defHud);
    }

    /**
     * 获取指定样式的 CSS 文件完整路径。
     */
    public static String getCssPath(StyleInfo style) {
        return BASE_CSS_DIR + style.cssFile;
    }

    /**
     * 从 styles.json 加载样式列表。
     *
     * 关键：使用 ResourceManager.getResourceStack() 合并所有资源包层级的 styles.json，
     * 而不是只取优先级最高的那一个。这样资源包只需提供自己的 styles.json（只包含新增样式），
     * 模组就能自动合并识别，不需要覆盖模组原版的 styles.json。
     */
    private static void loadStylesFromJson() {
        // 尝试从 ResourceManager 获取所有层级的 styles.json（资源包叠加）
        List<String> jsonContents = loadAllStyleJsons();

        if (jsonContents.isEmpty()) {
            // ResourceManager 不可用（如游戏启动早期），用 ClassLoader 回退
            String json = loadTemplate(STYLES_META_PATH);
            if (json.isEmpty()) {
                addBuiltinStyles();
                return;
            }
            jsonContents = List.of(json);
        }

        // 合并所有层级的样式，按 id 去重（高优先级覆盖低优先级）
        // getResourceStack 返回顺序：从低优先级（模组自带）到高优先级（资源包）
        // 所以后来的同名 id 会覆盖先来的
        Map<String, StyleInfo> merged = new LinkedHashMap<>();

        for (String json : jsonContents) {
            parseStyleJson(json, merged);
        }

        if (merged.isEmpty()) {
            addBuiltinStyles();
        } else {
            STYLES.addAll(merged.values());
        }
    }

    /** 解析单个 styles.json 并合并到 merged map 中 */
    private static void parseStyleJson(String json, Map<String, StyleInfo> merged) {
        try {
            Gson gson = new Gson();
            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root == null) return;
            JsonArray arr = root.getAsJsonArray("styles");
            if (arr == null) return;

            for (JsonElement elem : arr) {
                JsonObject obj = elem.getAsJsonObject();
                String id = obj.has("id") ? obj.get("id").getAsString() : "";
                String name = obj.has("name") ? obj.get("name").getAsString() : id;
                String desc = obj.has("description") ? obj.get("description").getAsString() : "";
                String file = obj.has("file") ? obj.get("file").getAsString() : "";
                boolean builtin = !obj.has("builtin") || obj.get("builtin").getAsBoolean();

                if (id.isEmpty() || file.isEmpty()) continue;

                // 验证 CSS 文件是否存在（ResourceManager 或 ClassLoader）
                String cssPath = BASE_CSS_DIR + file;
                if (!styleCssExists(cssPath)) {
                    CalendarMod.LOGGER.warn("样式 CSS 文件不存在: {}", cssPath);
                    continue;
                }

                // 解析 HUD 颜色配置（可选）
                HudColors hudColors = null;
                if (obj.has("hud") && obj.get("hud").isJsonObject()) {
                    JsonObject hudObj = obj.getAsJsonObject("hud");
                    int shadow = parseColor(hudObj, "shadow", 0x28000000);
                    int body = parseColor(hudObj, "body", 0xE8F7F7F8);
                    int decor = parseColor(hudObj, "decor", 0xFFE4E7EC);
                    int border = parseColor(hudObj, "border", 0x1A000000);
                    int textPrimary = parseColor(hudObj, "textPrimary", 0xFF1E293B);
                    int textSecondary = parseColor(hudObj, "textSecondary", 0xFF475569);
                    int textEvent = parseColor(hudObj, "textEvent", 0xFFB45309);
                    hudColors = new HudColors(shadow, body, decor, border,
                            textPrimary, textSecondary, textEvent);
                }

                // 同 id 则高优先级覆盖低优先级
                merged.put(id, new StyleInfo(id, name, desc, file, builtin, hudColors));
            }
        } catch (Exception e) {
            CalendarMod.LOGGER.error("解析 styles.json 失败", e);
        }
    }

    /**
     * 从 JsonObject 中解析颜色字段。
     * 支持 #RRGGBBAA、#RRGGBB、#AARRGGBB 或无前缀 hex，以及 0x 前缀。
     * 解析失败则返回 defaultVal。
     */
    private static int parseColor(JsonObject obj, String key, int defaultVal) {
        if (!obj.has(key)) return defaultVal;
        try {
            String raw = obj.get(key).getAsString().trim();
            if (raw.isEmpty()) return defaultVal;
            if (raw.startsWith("#")) raw = raw.substring(1);
            else if (raw.startsWith("0x") || raw.startsWith("0X")) raw = raw.substring(2);
            // 支持 3/4/6/8 位 hex
            if (raw.length() == 8) {
                // RRGGBBAA → 转换为 AARRGGBB
                long val = Long.parseLong(raw, 16);
                int rrggbb = (int) ((val >> 8) & 0xFFFFFF);
                int aa = (int) (val & 0xFF);
                return (aa << 24) | rrggbb;
            } else if (raw.length() == 6) {
                // RRGGBB → 完全不透明
                return 0xFF000000 | Integer.parseInt(raw, 16);
            } else if (raw.length() == 4) {
                // RGBA 缩写 → RRGGBBAA
                int r = Integer.parseInt(raw.substring(0, 1), 16);
                int g = Integer.parseInt(raw.substring(1, 2), 16);
                int b = Integer.parseInt(raw.substring(2, 3), 16);
                int a = Integer.parseInt(raw.substring(3, 4), 16);
                r = r * 16 + r; g = g * 16 + g; b = b * 16 + b; a = a * 16 + a;
                return (a << 24) | (r << 16) | (g << 8) | b;
            } else if (raw.length() == 3) {
                // RGB 缩写 → RRGGBB 不透明
                int r = Integer.parseInt(raw.substring(0, 1), 16);
                int g = Integer.parseInt(raw.substring(1, 2), 16);
                int b = Integer.parseInt(raw.substring(2, 3), 16);
                r = r * 16 + r; g = g * 16 + g; b = b * 16 + b;
                return 0xFF000000 | (r << 16) | (g << 8) | b;
            } else {
                // 尝试直接解析为十进制 int（0xAARRGGBB 格式）
                return (int) Long.parseLong(raw, 16);
            }
        } catch (Exception e) {
            return defaultVal;
        }
    }

    /**
     * 获取所有资源包层级的 styles.json 内容列表。
     * 顺序：从低优先级（模组自带）到高优先级（资源包覆盖/新增）。
     */
    private static List<String> loadAllStyleJsons() {
        List<String> results = new ArrayList<>();
        try {
            ResourceLocation rl = toResourceLocation(STYLES_META_PATH);
            if (rl == null) return results;

            // getResourceStack 返回从低到高优先级的所有资源
            List<Resource> stack = Minecraft.getInstance().getResourceManager().getResourceStack(rl);
            for (Resource res : stack) {
                String content = readAll(res.open());
                if (!content.isEmpty()) {
                    results.add(content);
                }
            }
        } catch (Exception ignored) {}
        return results;
    }

    /** 检查样式 CSS 文件是否存在（不读取内容，只验证） */
    private static boolean styleCssExists(String path) {
        try {
            ResourceLocation rl = toResourceLocation(path);
            if (rl != null) {
                Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(rl);
                if (res.isPresent()) return true;
            }
        } catch (Exception ignored) {}
        // ClassLoader 回退检查
        try {
            InputStream is = StyleManager.class.getClassLoader().getResourceAsStream(path);
            if (is != null) { is.close(); return true; }
            is = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
            if (is != null) { is.close(); return true; }
        } catch (Exception ignored) {}
        return false;
    }

    /** 添加内置样式作为后备 */
    private static void addBuiltinStyles() {
        // 默认灰白
        STYLES.add(new StyleInfo("default", "灰白(默认)", "默认灰白色调",
                "calendar_screen.css", true, new HudColors(
                        0x28000000, 0xE8F7F7F8, 0xFFE4E7EC, 0x1A000000,
                        0xFF1E293B, 0xFF475569, 0xFFB45309)));
        // 暗夜深色
        STYLES.add(new StyleInfo("dark", "暗夜", "深色暗色调",
                "styles/dark.css", true, new HudColors(
                        0x60000000, 0xE81E1E24, 0xFF2A2A32, 0x1AFFFFFF,
                        0xFFFFFFFF, 0xFFA0A0B0, 0xFFFFB74D)));
        // 海洋蓝
        STYLES.add(new StyleInfo("ocean", "海洋", "蓝色科技风",
                "styles/ocean.css", true, new HudColors(
                        0x301976D2, 0xE8E3F2FD, 0xFF1976D2, 0x201976D2,
                        0xFF0D47A1, 0xFF546E7A, 0xFFE65100)));
        // 森林绿
        STYLES.add(new StyleInfo("forest", "森林", "绿色自然风",
                "styles/forest.css", true, new HudColors(
                        0x282E7D32, 0xE8E8F5E9, 0xFF2E7D32, 0x182E7D32,
                        0xFF1B5E20, 0xFF388E3C, 0xFFE65100)));
        // 幻境紫
        STYLES.add(new StyleInfo("mystic", "幻境", "紫色神秘风",
                "styles/mystic.css", true, new HudColors(
                        0x286A1B9A, 0xE8F3E5F5, 0xFF6A1B9A, 0x186A1B9A,
                        0xFF4A148C, 0xFF7B1FA2, 0xFFE65100)));
        // 极简白
        STYLES.add(new StyleInfo("minimal", "极简", "简约扁平风",
                "styles/minimal.css", true, new HudColors(
                        0x14000000, 0xE8FAFAFA, 0xFFEEEEEE, 0x0A000000,
                        0xFF212121, 0xFF757575, 0xFFE65100)));
    }

    /** 从 ResourceManager 或 ClassLoader 加载文件 */
    private static String loadTemplate(String path) {
        // 1. ResourceManager（支持资源包覆盖）
        try {
            ResourceLocation rl = toResourceLocation(path);
            if (rl != null) {
                Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(rl);
                if (res.isPresent()) {
                    return readAll(res.get().open());
                }
            }
        } catch (Exception ignored) {}

        // 2. ClassLoader（模组自带）
        try {
            InputStream is = StyleManager.class.getClassLoader().getResourceAsStream(path);
            if (is == null) {
                is = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
            }
            if (is == null) return "";
            return readAll(is);
        } catch (Exception e) {
            return "";
        }
    }

    private static ResourceLocation toResourceLocation(String path) {
        if (!path.startsWith("assets/")) return null;
        String trimmed = path.substring("assets/".length());
        int slash = trimmed.indexOf('/');
        if (slash <= 0) return null;
        String namespace = trimmed.substring(0, slash);
        String rest = trimmed.substring(slash + 1);
        try {
            return ResourceLocation.fromNamespaceAndPath(namespace, rest);
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
}