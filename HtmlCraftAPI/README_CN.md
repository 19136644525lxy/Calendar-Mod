# HtmlCraft API

> Minecraft 1.20.1 Forge / Fabric HTML/CSS GUI 渲染引擎

## 简介

**HtmlCraft API** 是一个轻量级的 HTML/CSS 渲染引擎，将 Web 前端技术引入 Minecraft GUI 开发。开发者无需学习复杂的 Minecraft GUI API（`GuiGraphics`、`Widget`、`AbstractContainerScreen` 等），只需编写 HTML + CSS 即可构建精美的游戏界面。

---

## 特性

| 特性 | 说明 |
|------|------|
| HTML 解析 | 支持常见 HTML 标签、属性、嵌套结构、HTML 实体（命名/十进制/十六进制） |
| CSS 样式 | 支持类选择器、ID 选择器、标签选择器、内联样式 |
| 布局引擎 | Block 布局、Flexbox 弹性布局、CSS Grid 网格布局 |
| 视觉效果 | 线性渐变、圆角边框、阴影模拟、透明度 |
| 交互系统 | 按钮点击事件、鼠标滚轮滚动容器、命中测试 |
| 模板引擎 | `{{variable}}` 变量替换、数据绑定上下文 |
| 线程安全 | ConcurrentHashMap、CopyOnWriteArrayList |

---

## 环境要求

本 API 同时提供 Forge 和 Fabric 两个版本，按所用加载器选择对应 jar 即可。

### Forge 版

| 依赖 | 版本 |
|------|------|
| Minecraft | 1.20.1 |
| Forge | 47.x |
| Java | 17 |

### Fabric 版

| 依赖 | 版本 |
|------|------|
| Minecraft | 1.20.1 |
| Fabric Loader | >=0.19.3 |
| Fabric API | 0.92.11+1.20.1 |
| Java | 17 |

---

## 安装

### 作为模组前置依赖

- Forge：将 `htmlcraftapi-1.0.0-1.20.1forge.jar` 放入 `mods` 文件夹
- Fabric：将 `htmlcraftapi-1.0.0-1.20.1fabric.jar` 放入 `mods` 文件夹

### 作为开发依赖

**Forge 版**（Mojang 官方映射）：

```gradle
dependencies {
    implementation files('libs/htmlcraftapi-1.0.0-1.20.1forge.jar')
}
```

**Fabric 版**（Yarn 映射，配合 Loom 复合构建）：

```gradle
dependencies {
    modImplementation 'com.htmlcraft.api:htmlcraftapi:1.0.0-1.20.1fabric'
}
```

Fabric 版推荐通过 `includeBuild` 引入源码工程以便调试：

```gradle
// settings.gradle
includeBuild '../HtmlCraftAPI/fabric'
```

---

## 快速上手

> 以下示例使用 Forge (Mojang 映射) API。Fabric 版仅类名存在差异（如 `Component` → `Text`、`Minecraft` → `MinecraftClient`、`GuiGraphics` → `DrawContext`），HTML/CSS API 完全一致。

### 1. 通过 Builder 创建界面

```java
import com.htmlcraft.api.HtmlRendererAPI;
import com.htmlcraft.api.binding.DataContext;
import com.htmlcraft.api.screen.HtmlScreen;
import net.minecraft.network.chat.Component;

DataContext data = new DataContext();
data.set("title", "我的界面");
data.set("content", "Hello, Minecraft!");

HtmlScreen screen = HtmlRendererAPI.createScreen(Component.literal("My Screen"))
    .html("<div class='container'>" +
          "  <h1>{{title}}</h1>" +
          "  <p>{{content}}</p>" +
          "  <button id='btn-ok'>确定</button>" +
          "</div>")
    .css(".container { padding: 20px; background: #1a1a2e; border-radius: 10px; }" +
         "h1 { color: #e94560; font-size: 16px; }" +
         "p { color: #ffffff; }" +
         "#btn-ok { background: #0f3460; color: #ffffff; border-radius: 6px; padding: 8px; }")
    .data(data)
    .onClick(event -> {
        if ("btn-ok".equals(event.element().getId())) {
            System.out.println("玩家点击了确定按钮");
        }
    })
    .build();

Minecraft.getInstance().setScreen(screen);
```

### 2. 继承 HtmlScreen 自定义界面

```java
public class MyScreen extends HtmlScreen {

    public MyScreen() {
        super(Component.literal("My Screen"));
    }

    @Override
    protected String getHtml() {
        return "<div class='root'>自定义内容</div>";
    }

    @Override
    protected String getCss() {
        return ".root { padding: 16px; background: #2d2d44; }";
    }
}
```

### 3. Forge / Fabric 映射对照

| Forge (Mojang) | Fabric (Yarn) | 说明 |
|-----------------|---------------|------|
| `net.minecraft.network.chat.Component` | `net.minecraft.text.Text` | 文本组件 |
| `net.minecraft.client.Minecraft` | `net.minecraft.client.MinecraftClient` | 客户端实例 |
| `net.minecraft.client.gui.GuiGraphics` | `net.minecraft.client.gui.DrawContext` | 绘制上下文 |
| `net.minecraft.world.level.saveddata.SavedData` | `net.minecraft.world.PersistentState` | 存档持久化 |
| `net.minecraft.network.FriendlyByteBuf` | `net.minecraft.network.PacketByteBuf` | 网络缓冲区 |
| `net.minecraftforge.network.SimpleChannel` | `ServerPlayNetworking` / `ClientPlayNetworking` | 网络通道 |

---

## 核心 API

### HtmlRendererAPI

统一入口，提供 Builder 模式创建 HTML Screen。

| 方法 | 说明 |
|------|------|
| `createScreen(Component title)` | 创建 Screen 构建器 |

### HtmlRendererAPI.Builder

链式构建器，配置 HTML、CSS、数据和事件。

| 方法 | 说明 |
|------|------|
| `html(String html)` | 设置 HTML 内容 |
| `css(String css)` | 设置 CSS 样式表 |
| `data(DataContext context)` | 设置模板数据上下文 |
| `onClick(Consumer<ClickEvent> handler)` | 设置点击事件回调 |
| `build()` | 构建 HtmlScreen 实例 |

### HtmlScreen

HTML 渲染屏幕基类，继承自 Minecraft `Screen`。

| 方法 | 说明 |
|------|------|
| `getHtml()` | 抽象方法，子类提供 HTML 内容 |
| `getCss()` | 可重写，子类提供 CSS 样式 |
| `setClickHandler(Consumer<ClickEvent>)` | 设置点击事件处理器 |
| `setPreferredSize(int w, int h)` | 设置内容区尺寸（居中布局） |
| `rebuild()` | 重建渲染管线（数据变更后刷新） |
| `getRootLayout()` | 获取布局根节点 |
| `getPipeline()` | 获取渲染管线 |

### HtmlScreen.ClickEvent

点击事件，包含被点击的元素和坐标。

| 方法 | 说明 |
|------|------|
| `element()` | 被点击的 DOM 元素 |
| `button()` | 鼠标按键（0=左键，1=右键，2=中键） |
| `x()` / `y()` | 点击坐标 |

### DataContext

模板变量上下文，线程安全。

| 方法 | 说明 |
|------|------|
| `set(String key, Object value)` | 设置变量 |
| `get(String key)` | 获取变量 |
| `getString(String key)` | 获取字符串值 |
| `has(String key)` | 检查变量是否存在 |
| `clear()` | 清除所有变量 |

### HtmlElement

DOM 元素节点。

| 方法 | 说明 |
|------|------|
| `tagName()` | 获取标签名 |
| `getId()` | 获取 id 属性 |
| `hasClass(String)` | 检查是否包含 class |
| `getAttribute(String)` | 获取属性值 |
| `children()` | 获取子节点列表 |

---

## 支持的 CSS 属性

### 布局

| 属性 | 可选值 |
|------|--------|
| `display` | `block` / `flex` / `inline` / `grid` / `none` |
| `flex-direction` | `row` / `column` |
| `justify-content` | `flex-start` / `center` / `flex-end` / `space-between` / `space-around` |
| `align-items` | `flex-start` / `center` / `flex-end` / `stretch` |
| `gap` | 像素值 |
| `grid-template-columns` | `repeat(N, 1fr)` |

### 盒模型

| 属性 | 说明 |
|------|------|
| `width` / `height` | 像素值，`auto` |
| `padding` | 像素值 |
| `margin` | 像素值 |
| `border-width` | 像素值 |

### 视觉效果

| 属性 | 可选值 |
|------|--------|
| `background` | `#RRGGBB` / `#AARRGGBB` / `#RRGGBBAA` |
| `background` (渐变) | `linear-gradient(to bottom, #color1, #color2)` |
| `color` | 文字颜色 |
| `border-color` | 边框颜色 |
| `border-radius` | 圆角像素值 |
| `box-shadow` | `offsetX offsetY blur spread #color` |
| `opacity` | `0.0` - `1.0` |

### 文本

| 属性 | 可选值 |
|------|--------|
| `font-size` | 像素值 |
| `text-align` | `left` / `center` / `right` |

### 定位与溢出

| 属性 | 可选值 |
|------|--------|
| `position` | `static` / `relative` / `absolute` / `fixed` |
| `overflow-x` / `overflow-y` | `visible` / `hidden` / `scroll` / `auto` |
| `z-index` | 整数 |

---

## 模板引擎

HTML 中使用 `{{variable}}` 占位符，在 `init()` 阶段由 `TemplateEngine.render()` 替换为 `DataContext` 中的值。

```java
DataContext data = new DataContext();
data.set("player_name", "Steve");
data.set("level", 30);

// HTML 模板
String html = "<div>玩家: {{player_name}} | 等级: {{level}}</div>";
```

特殊字符会被自动转义为 HTML 实体，防止注入。

---

## 渲染管线

```
HTML 字符串
    ↓
HtmlParser（解析为 DOM 树）
    ↓
StyleSheet + CssParser（加载 CSS 规则）
    ↓
StyleCalculator（计算每个元素的最终样式）
    ↓
LayoutEngine（执行布局，生成 LayoutNode 树）
    ↓
RenderPipeline（生成并执行渲染指令）
    ↓
GuiGraphics（绘制到 Minecraft 屏幕）
```

---

## 包结构

```
com.htmlcraft.api
├── HtmlCraftAPI.java          // 模组主类
├── HtmlRendererAPI.java       // 对外统一入口 + Builder
├── core/
│   ├── HtmlDocument.java      // HTML 文档
│   ├── HtmlElement.java       // DOM 元素节点
│   ├── HtmlNode.java          // DOM 节点接口
│   └── HtmlText.java          // DOM 文本节点
├── parser/
│   ├── HtmlParser.java        // HTML 解析器
│   └── CssParser.java         // CSS 解析器
├── style/
│   ├── StyleSheet.java        // CSS 样式表
│   ├── StyleCalculator.java   // 样式计算器
│   └── ComputedStyle.java     // 计算后的样式
├── layout/
│   ├── LayoutEngine.java      // 布局引擎
│   └── LayoutNode.java        // 布局节点
├── render/
│   ├── RenderPipeline.java    // 渲染管线
│   ├── RenderContext.java     // 渲染上下文
│   ├── RenderCommand.java     // 渲染指令接口
│   └── commands/              // 具体渲染指令
│       ├── FillRectCommand.java
│       ├── DrawTextCommand.java
│       ├── DrawBorderCommand.java
│       └── BlitTextureCommand.java
├── binding/
│   ├── DataContext.java       // 数据绑定上下文
│   └── TemplateEngine.java    // 模板引擎
└── screen/
    └── HtmlScreen.java        // HTML 渲染屏幕基类
```

---

## 点击事件

可点击元素包括：
- `<button>` 标签
- `<a>` 标签
- 带有 `id` 属性的任意元素

点击后通过 `ClickEvent` 回调，可通过 `event.element().getId()` 判断点击了哪个元素。

---

## 滚动支持

对设置了 `overflow-y: scroll` 或 `overflow-y: auto` 的元素，鼠标滚轮在其区域内滚动时会自动触发滚动。每步滚动 20 像素。

---

## 许可证

LGPL-2.1

## 作者

Yifei
