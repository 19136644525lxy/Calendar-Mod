# Calendar Mod 资源包制作教程

> 本教程详细说明如何通过资源包自定义日历界面样式。

---

## 目录

1. [快速开始](#快速开始)
2. [资源包结构](#资源包结构)
3. [pack.mcmeta](#packmcmeta)
4. [styles.json 详解](#stylesjson-详解)
5. [HUD 颜色配置](#hud-颜色配置)
6. [CSS 变量参考](#css-变量参考)
7. [CSS 类名参考](#css-类名参考)
8. [支持的 CSS 属性](#支持的-css-属性)
9. [颜色格式](#颜色格式)
10. [HTML 模板参考](#html-模板参考)
11. [完整示例](#完整示例)
12. [常见问题](#常见问题)

---

## 快速开始

只需 3 步即可创建自定义样式：

1. 创建资源包文件夹，放入 `pack.mcmeta`
2. 在 `assets/calendarmod/templates/styles/` 下放置你的 CSS 文件
3. 在 `assets/calendarmod/templates/styles.json` 中声明你的样式

启用资源包后，游戏内 日历 → 客户端设置 → 界面样式 即可看到新样式。

---

## 资源包结构

```
my_calendar_style/
├── pack.mcmeta
└── assets/
    └── calendarmod/
        └── templates/
            ├── styles.json                    ← 声明新增/覆盖的样式
            └── styles/
                ├── my_style.css               ← 你的自定义 CSS
                ├── another_style.css          ← 可以添加多个
                └── ...
```

### 两种使用方式

| 方式 | 说明 |
|------|------|
| **新增样式** | 在 `styles.json` 中只列出你的新样式，模组会自动与内置 6 套样式合并 |
| **覆盖样式** | 在 `styles.json` 中使用与内置样式相同的 `id`（如 `dark`），高优先级资源包会覆盖低优先级 |

---

## pack.mcmeta

```json
{
  "pack": {
    "pack_format": 15,
    "description": "我的日历自定义样式"
  }
}
```

| 字段 | 说明 |
|------|------|
| `pack_format` | 1.20.1 对应 `15` |
| `description` | 资源包描述，显示在资源包选择界面 |

---

## styles.json 详解

这是样式的元数据文件，告诉模组有哪些样式可用。

### 完整格式

```json
{
  "styles": [
    {
      "id": "my_style",
      "name": "我的样式",
      "description": "这是一个自定义样式",
      "file": "styles/my_style.css",
      "builtin": false
    }
  ]
}
```

### 字段说明

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | String | 是 | 样式唯一标识符，英文小写+下划线，如 `my_style`。与内置 id 相同则覆盖 |
| `name` | String | 否 | 显示名称，显示在配置界面下拉框。默认与 id 相同 |
| `description` | String | 否 | 样式描述，仅用于记录 |
| `file` | String | 是 | CSS 文件相对 `templates/` 的路径，如 `styles/my_style.css` |
| `builtin` | Boolean | 否 | 是否为内置样式。资源包样式应设为 `false`。默认 `true` |
| `hud` | Object | 否 | HUD 颜色配置对象，自定义该样式下小日历 HUD 的配色。详见 [HUD 颜色配置](#hud-颜色配置) 章节。不写则使用默认灰白配色 |

### id 命名规则

- 只允许小写字母、数字、下划线
- 不能以数字开头
- **示例**：`my_style`、`neon_blue`、`retro_80s`

### 内置 id（覆盖时使用）

| id | 说明 |
|----|------|
| `default` | 灰白(默认) |
| `dark` | 暗夜 |
| `ocean` | 海洋 |
| `forest` | 森林 |
| `mystic` | 幻境 |
| `minimal` | 极简 |

### 多样式声明

一个 `styles.json` 可以声明多个样式：

```json
{
  "styles": [
    {
      "id": "neon",
      "name": "霓虹",
      "description": "赛博朋克霓虹风",
      "file": "styles/neon.css",
      "builtin": false
    },
    {
      "id": "sakura",
      "name": "樱花",
      "description": "粉色樱花风",
      "file": "styles/sakura.css",
      "builtin": false
    }
  ]
}
```

---

## HUD 颜色配置

日历 HUD（屏幕右上角的小日历框）会跟随玩家选中的样式自动切换配色。通过在 `styles.json` 的样式条目中添加可选的 `hud` 字段即可自定义该样式的 HUD 配色。

### 基本语法

在样式对象中增加一个 `hud` 子对象：

```json
{
  "id": "neon",
  "name": "霓虹",
  "description": "赛博朋克霓虹风",
  "file": "styles/neon.css",
  "builtin": false,
  "hud": {
    "shadow": "#FF00FF28",
    "body": "#0F0F23E8",
    "decor": "#FF00FFFF",
    "border": "#00FFFF1A",
    "textPrimary": "#00FFFFFFF",
    "textSecondary": "#FF00FF80",
    "textEvent": "#FFFF00FF"
  }
}
```

### 颜色字段说明

| 字段 | 用途 | 渲染位置 |
|------|------|----------|
| `shadow` | 阴影颜色（多层外扩渐变） | HUD 外围 4 层外扩矩形 |
| `body` | 主体背景色 | HUD 主背景矩形 |
| `decor` | 顶部装饰条颜色 | HUD 顶部 3px 高的装饰带 |
| `border` | 细边框颜色 | HUD 主体的 1px 圆角边框 |
| `textPrimary` | 主文字颜色（第 1 行：纪元 年 月） | HUD 第一行 |
| `textSecondary` | 次要文字颜色（第 2 行：日 星期） | HUD 第二行及以后（非事件行） |
| `textEvent` | 事件文字颜色（◆ 开头的事件行） | HUD 中以 ◆ 开头的事件行 |

### 颜色格式

HUD 颜色字段支持以下 hex 格式，**alpha 通道位于末尾**（与 CSS 中 `#RRGGBBAA` 一致）：

| 格式 | 示例 | 说明 |
|------|------|------|
| `#RRGGBB` | `#FF5733` | 6 位 hex，完全不透明 |
| `#RRGGBBAA` | `#FF573380` | 8 位 hex，AA=透明度（00 完全透明，FF 不透明） |
| `#RGB` | `#F53` | 3 位缩写，完全不透明 |
| `#RGBA` | `#F538` | 4 位缩写，AA 在末尾 |
| `0xAARRGGBB` | `0x80FF5733` | Java 风格，AA 在开头（不推荐，建议用 `#` 前缀） |

> **注意**：HUD 颜色字段**不支持** `rgba()` 函数和 CSS 变量占位符（如 `__XXX__`）。这些颜色是 Java 端直接解析并用于 `GuiGraphics` 绘制的整数，与 CSS 渲染引擎无关。

### 字段省略规则

`hud` 字段及其中所有子字段都是**可选的**：

- **完全不写 `hud` 字段**：使用默认灰白配色
- **只写部分子字段**：未写的字段使用默认值（与 `default` 样式对应字段相同）

默认值参考：

| 字段 | 默认值 | 说明 |
|------|--------|------|
| `shadow` | `#00000028` | 25% 不透明度黑色 |
| `body` | `#F7F7F8E8` | 91% 不透明度浅灰白 |
| `decor` | `#E4E7ECFF` | 亮灰色 |
| `border` | `#0000001A` | 10% 不透明度黑色 |
| `textPrimary` | `#1E293BFF` | 深灰蓝色 |
| `textSecondary` | `#475569FF` | 中灰色 |
| `textEvent` | `#B45309FF` | 琥珀色 |

### 内置样式的 HUD 配色

模组自带的 6 套样式均预配置了 HUD 颜色，可参考或直接覆盖：

| 样式 | body | decor | textPrimary | textEvent |
|------|------|-------|-------------|-----------|
| 灰白(默认) | `#F7F7F8E8` | `#E4E7ECFF` | `#1E293BFF` | `#B45309FF` |
| 暗夜 | `#1E1E24E8` | `#2A2A32FF` | `#FFFFFFFF` | `#FFB74DFF` |
| 海洋 | `#E3F2FDE8` | `#1976D2FF` | `#0D47A1FF` | `#E65100FF` |
| 森林 | `#E8F5E9E8` | `#2E7D32FF` | `#1B5E20FF` | `#E65100FF` |
| 幻境 | `#F3E5F5E8` | `#6A1B9AFF` | `#4A148CFF` | `#E65100FF` |
| 极简 | `#FAFAFAE8` | `#EEEEEEFF` | `#212121FF` | `#E65100FF` |

### 同步机制说明

- HUD 颜色与日历界面样式**完全同步**：玩家在客户端设置中切换样式后，HUD 配色会**立即生效**，无需重启游戏
- 覆盖内置样式时（同 `id`）：资源包的 `hud` 配置会**完全替换**模组自带的 HUD 配色（不会字段级合并）
- 资源包样式不写 `hud` 字段：使用默认灰白 HUD 配色，不会继承被覆盖样式的 HUD 颜色

### 完整示例：自定义 HUD 配色

```json
{
  "styles": [
    {
      "id": "neon",
      "name": "霓虹",
      "description": "赛博朋克霓虹风",
      "file": "styles/neon.css",
      "builtin": false,
      "hud": {
        "shadow": "#FF00FF28",
        "body": "#0F0F23E8",
        "decor": "#FF00FFFF",
        "border": "#00FFFF1A",
        "textPrimary": "#00FFFFFFF",
        "textSecondary": "#FF00FF80",
        "textEvent": "#FFFF00FF"
      }
    }
  ]
}
```

这套配色会让 HUD 显示为：深紫黑半透明背景 + 品红装饰条 + 青色主文字 + 品红次要文字 + 黄色事件文字，与霓虹风格 CSS 视觉统一。

---

## CSS 变量参考

CSS 文件中**必须使用**以下变量占位符，它们在运行时由 Java 根据屏幕分辨率动态计算替换为像素值。

**如果省略某个变量，对应的 CSS 属性值会变成 `__XXX__px` 这样的无效值，导致布局错乱。**

### 所有动态变量

| 变量 | 说明 | 计算公式 | 最小值 |
|------|------|----------|--------|
| `__ROOT_PAD__` | 根容器内边距 | `screenH / 30` | 6 |
| `__GAP__` | 元素间距 | `screenH / 50` | 4 |
| `__HEADER_PAD__` | 标题区内边距 | `screenH / 35` | 6 |
| `__NAV_BTN__` | 导航按钮尺寸 | `screenH / 12` | 28 |
| `__TITLE_FONT__` | 标题字号 | `screenH / 22` | 14 |
| `__ERA_FONT__` | 纪元字号 | `screenH / 36` | 9 |
| `__SUBTITLE_FONT__` | 副标题字号 | `screenH / 36` | 9 |
| `__WEEK_PAD__` | 星期格内边距 | `screenH / 60` | 3 |
| `__WEEK_FONT__` | 星期字号 | `screenH / 30` | 10 |
| `__DAY_GAP__` | 日期格间距 | `screenH / 60` | 3 |
| `__DAY_H__` | 日期格高度 | 动态剩余空间 / 6 | 20 |
| `__DAY_PAD__` | 日期格内边距 | `dayH / 10` | 2 |
| `__DAY_NUM_FONT__` | 日期数字字号 | `dayH / 2` | 11 |
| `__DAY_EVENT_FONT__` | 日期事件文字字号 | `dayH / 4` | 8 |
| `__BTN__` | 底部按钮宽度 | `(screenW - rootPad*2 - gap*2) / 3` | 50 |
| `__BTN_H__` | 底部按钮高度 | `screenH / 16` | 18 |
| `__BTN_FONT__` | 底部按钮字号 | `screenH / 32` | 9 |
| `__EVENTS_H__` | 事件面板高度 | `screenH / 5` | 50 |
| `__EVENT_ROW_PAD__` | 事件行内边距 | `eventsPanelH / 20` | 3 |
| `__EVENT_FONT__` | 事件名字字号 | `eventsPanelH / 14` | 10 |
| `__EVENT_DESC_FONT__` | 事件描述字号 | `eventsPanelH / 18` | 8 |

### 使用方式

在 CSS 中直接写入变量名，后接 `px`：

```css
.cal-root {
    padding: __ROOT_PAD__px;
    gap: __GAP__px;
}
.cal-day {
    height: __DAY_H__px;
    font-size: __DAY_NUM_FONT__px;
}
```

**注意**：变量替换是纯文本替换，所以 `__DAY_H__px` 会被替换为 `45px` 这样的值。不要在变量名前后加空格。

### 可以不使用变量的属性

以下属性不涉及动态尺寸，可以直接写固定像素值：

- `border-radius`
- `border-width`
- `margin`
- `opacity`
- `gap`（如果不关心自适应可以写固定值）
- 所有颜色值

---

## CSS 类名参考

以下是 HTML 模板中使用的所有类名。你的 CSS **必须覆盖这些类**才能正确渲染界面。

### 结构类

| 类名 | 说明 | HTML 结构 |
|------|------|-----------|
| `.cal-root` | 根容器，包含所有内容 | `<div class="cal-root">` |
| `.cal-header` | 顶部标题区 | `<div class="cal-header">` |
| `.cal-nav-btn` | 月份导航按钮（◀ ▶） | `<button class="cal-nav-btn">` |
| `.cal-title-wrap` | 标题文字容器 | `<div class="cal-title-wrap">` |
| `.cal-era` | 纪元名称 | `<div class="cal-era">` |
| `.cal-title` | 年月标题 | `<div class="cal-title">` |
| `.cal-subtitle` | 副标题（事件数+星期） | `<div class="cal-subtitle">` |
| `.cal-week-row` | 星期标题行容器 | `<div class="cal-week-row">` |
| `.cal-week-cell` | 星期单元格 | `<div class="cal-week-cell">` |
| `.cal-grid` | 日期网格容器 | `<div class="cal-grid">` |
| `.cal-day` | 日期单元格 | `<div class="cal-day">` |
| `.cal-day-head` | 日期格头部（数字+圆点） | `<div class="cal-day-head">` |
| `.cal-day-num` | 日期数字 | `<div class="cal-day-num">` |
| `.cal-day-dot` | 事件指示圆点 | `<div class="cal-day-dot">` |
| `.cal-day-event` | 日期格内事件名 | `<div class="cal-day-event">` |
| `.cal-day-count` | 额外事件数量（+N） | `<div class="cal-day-count">` |
| `.cal-events-panel` | 今日事件面板 | `<div class="cal-events-panel">` |
| `.cal-panel-header` | 面板标题行 | `<div class="cal-panel-header">` |
| `.cal-panel-title` | 面板标题 | `<div class="cal-panel-title">` |
| `.cal-panel-stat` | 面板统计信息 | `<div class="cal-panel-stat">` |
| `.cal-no-event` | 无事件提示 | `<div class="cal-no-event">` |
| `.cal-event-row` | 事件行 | `<div class="cal-event-row">` |
| `.cal-event-icon` | 事件图标（◆） | `<div class="cal-event-icon">` |
| `.cal-event-content` | 事件内容容器 | `<div class="cal-event-content">` |
| `.cal-event-name` | 事件名称 | `<div class="cal-event-name">` |
| `.cal-event-desc` | 事件描述 | `<div class="cal-event-desc">` |
| `.cal-event-tag` | 事件标签（↻ 每年重复） | `<div class="cal-event-tag">` |
| `.cal-bottom` | 底部按钮栏 | `<div class="cal-bottom">` |
| `.cal-btn` | 底部按钮 | `<button class="cal-btn">` |

### 状态修饰类

这些类会**额外添加**到基础类上，用于标识特殊状态。在 CSS 中用 `.class.state` 选择器组合使用。

| 修饰类 | 应用到 | 说明 |
|--------|--------|------|
| `.empty` | `.cal-day` | 空白日期格（月份开头补位） |
| `.today` | `.cal-day` | 今天 |
| `.future` | `.cal-day` | 未来日期 |
| `.has-event` | `.cal-day` | 有事件的日期 |
| `.multi-event` | `.cal-day` | 有 2 个以上事件的日期 |
| `.sun` | `.cal-week-cell` | 星期日（第一个） |
| `.sat` | `.cal-week-cell` | 星期六（最后一个） |

**示例**：

```css
/* 普通日期格 */
.cal-day { ... }

/* 今天的日期格 */
.cal-day.today { ... }

/* 有事件的日期格 */
.cal-day.has-event { ... }

/* 今天且有事件 */
.cal-day.today.has-event { ... }

/* 星期日 */
.cal-week-cell.sun { ... }
```

---

## 支持的 CSS 属性

### 布局

| 属性 | 可选值 | 示例 |
|------|--------|------|
| `display` | `block` / `flex` / `inline` / `grid` / `none` | `display: flex;` |
| `flex-direction` | `row` / `column` | `flex-direction: column;` |
| `justify-content` | `flex-start` / `center` / `flex-end` / `space-between` / `space-around` | `justify-content: center;` |
| `align-items` | `flex-start` / `center` / `flex-end` / `stretch` | `align-items: center;` |
| `gap` | 像素值 | `gap: 4px;` |
| `grid-template-columns` | `repeat(N, 1fr)` | `grid-template-columns: repeat(7, 1fr);` |

### 尺寸

| 属性 | 可选值 | 示例 |
|------|--------|------|
| `width` | 像素值 / `auto` | `width: 40px;` |
| `height` | 像素值 / `auto` | `height: __DAY_H__px;` |
| `padding` | 像素值 | `padding: 8px;` |
| `margin` | 像素值 | `margin-bottom: 4px;` |

### 视觉效果

| 属性 | 可选值 | 示例 |
|------|--------|------|
| `background` | 纯色 / 渐变 | 见下方颜色格式 |
| `color` | 文字颜色 | `color: #FFFFFF;` |
| `border-width` | 像素值 | `border-width: 1px;` |
| `border-color` | 边框颜色 | `border-color: #FFFFFF20;` |
| `border-radius` | 像素值 | `border-radius: 10px;` |
| `box-shadow` | `offsetX offsetY blur spread color` | `box-shadow: 0 4px 12px rgba(0,0,0,0.2);` |
| `opacity` | `0.0` - `1.0` | `opacity: 0.5;` |

### 文本

| 属性 | 可选值 | 示例 |
|------|--------|------|
| `font-size` | 像素值 | `font-size: 14px;` |
| `text-align` | `left` / `center` / `right` | `text-align: center;` |
| `line-height` | 像素值 | `line-height: 30px;` |

### 定位

| 属性 | 可选值 | 示例 |
|------|--------|------|
| `position` | `static` / `relative` / `absolute` / `fixed` | `position: absolute;` |
| `top` / `right` / `bottom` / `left` | 像素值 | `top: 0; right: 2px;` |
| `z-index` | 整数 | `z-index: 10;` |

### 溢出

| 属性 | 可选值 | 示例 |
|------|--------|------|
| `overflow-x` | `visible` / `hidden` / `scroll` / `auto` | `overflow-x: hidden;` |
| `overflow-y` | `visible` / `hidden` / `scroll` / `auto` | `overflow-y: scroll;` |

---

## 颜色格式

### 支持的格式

| 格式 | 示例 | 说明 |
|------|------|------|
| `#RRGGBB` | `#FF5733` | 6位十六进制，不透明 |
| `#AARRGGBB` | `#80FF5733` | 8位十六进制，AA=透明度（00完全透明，FF不透明） |
| `#RRGGBBAA` | `#FF573380` | 8位十六进制，AA在末尾 |
| `rgba(r,g,b,a)` | `rgba(0,0,0,0.2)` | RGBA 函数，a 为 0.0-1.0 |

### 渐变

```css
/* 线性渐变 */
background: linear-gradient(to bottom, #1A1A2E, #16213E);
background: linear-gradient(to right, #F8F8F8FA, #F0F0F3FA);
background: linear-gradient(to bottom, #2A2A32F2, #22222AF2);
```

**方向关键字**：`to bottom`、`to top`、`to left`、`to right`

---

## HTML 模板参考

通过资源包覆盖 `assets/calendarmod/templates/calendar_screen.html` 可以自定义界面结构。

### 默认 HTML 模板

```html
<div class="cal-root">
  <div class="cal-header">
    <button id="prev-month" class="cal-nav-btn">&#9664;</button>
    <div class="cal-title-wrap">
      <div class="cal-era">{{era_name}}</div>
      <div class="cal-title">{{year}}{{year_label}} {{month_name}}</div>
      <div class="cal-subtitle">{{month_events}}事件 &middot; {{today_weekday}}</div>
    </div>
    <button id="next-month" class="cal-nav-btn">&#9654;</button>
  </div>

  <div class="cal-week-row">
    {{week_row}}
  </div>

  <div class="cal-grid">
    {{day_cells}}
  </div>

  <div id="events-scroll" class="cal-events-panel">
    <div class="cal-panel-header">
      <div class="cal-panel-title">&#128197; {{today_events_label}}</div>
      <div class="cal-panel-stat">{{year}}{{year_label}} {{month_name}}</div>
    </div>
    {{events_content}}
  </div>

  <div class="cal-bottom">
    <button id="open-client-config" class="cal-btn">&#9881; {{client_config_label}}</button>
    <button id="open-calendar-config" class="cal-btn">&#128197; {{calendar_config_label}}</button>
    <button id="open-events" class="cal-btn">&#10133; {{add_event_label}}</button>
  </div>
</div>
```

### 模板变量占位符

HTML 中使用 `{{variable}}` 格式的占位符，在运行时由 Java 自动替换为实际值。**自定义模板必须保留所有占位符**，否则对应位置会空白。

#### 文本变量

| 占位符 | 替换内容 | 示例值 |
|--------|----------|--------|
| `{{era_name}}` | 纪元名称 | 星历 |
| `{{year}}` | 当前年份数字 | 3 |
| `{{year_label}}` | "年"字（本地化） | 年 |
| `{{month_name}}` | 当前月份名称 | 星月 |
| `{{month_events}}` | 当月事件数量 | 5 |
| `{{today_weekday}}` | 今日星期名称 | 星期三 |
| `{{today_events_label}}` | "今日事件"文字（本地化） | 今日事件 |
| `{{client_config_label}}` | "客户端设置"文字（本地化） | 客户端设置 |
| `{{calendar_config_label}}` | "历法设置"文字（本地化） | 历法设置 |
| `{{add_event_label}}` | "添加事件"文字（本地化） | 添加事件 |

#### 动态生成变量

这些变量的内容是由 Java 代码动态生成的 HTML 片段，**不能手动编写**，只能通过占位符引入。

| 占位符 | 生成内容 | 结构说明 |
|--------|----------|----------|
| `{{week_row}}` | 星期标题行 | 7 个 `<div class="cal-week-cell">` |
| `{{day_cells}}` | 日期网格 | 若干 `<div class="cal-day">` |
| `{{events_content}}` | 今日事件列表 | 若干 `<div class="cal-event-row">` 或无事件提示 |

### 动态生成的 HTML 结构

#### `{{week_row}}` 生成内容

```html
<div class="cal-week-cell sun">星期日</div>
<div class="cal-week-cell">星期一</div>
<div class="cal-week-cell">星期二</div>
<div class="cal-week-cell">星期三</div>
<div class="cal-week-cell">星期四</div>
<div class="cal-week-cell">星期五</div>
<div class="cal-week-cell sat">星期六</div>
```

- 第一个单元格额外添加 `sun` 类
- 最后一个单元格额外添加 `sat` 类
- 星期名称由历法配置决定

#### `{{day_cells}}` 生成内容

```html
<!-- 月初补位（空格） -->
<div class="cal-day empty"></div>

<!-- 普通日期 -->
<div id="day-1" class="cal-day">
  <div class="cal-day-head">
    <div class="cal-day-num">1</div>
  </div>
</div>

<!-- 今天 -->
<div id="day-15" class="cal-day today">
  <div class="cal-day-head">
    <div class="cal-day-num">15</div>
    <div class="cal-day-dot"></div>
  </div>
  <div class="cal-day-event">事件名</div>
</div>

<!-- 有多个事件的日期 -->
<div id="day-20" class="cal-day today has-event multi-event">
  <div class="cal-day-head">
    <div class="cal-day-num">20</div>
    <div class="cal-day-dot"></div>
  </div>
  <div class="cal-day-event">第一个事件</div>
  <div class="cal-day-count">+2</div>
</div>

<!-- 未来日期 -->
<div id="day-25" class="cal-day future">
  <div class="cal-day-head">
    <div class="cal-day-num">25</div>
  </div>
</div>
```

**日期格的 class 组合规则**：
- 基础类：`cal-day`
- 空白补位：追加 `empty`
- 今天：追加 `today`
- 未来日期：追加 `future`（与 `today` 互斥）
- 有事件：追加 `has-event`
- 事件数 >= 2：追加 `multi-event`

**日期格的 id**：`day-<日期数字>`，如 `day-1`、`day-15`

#### `{{events_content}}` 生成内容

无事件时：
```html
<div class="cal-no-event">✕ 今日无事件</div>
```

有事件时：
```html
<div class="cal-event-row" style="border-left-color:#FFD700">
  <div class="cal-event-icon" style="color:#FFD700">◆</div>
  <div class="cal-event-content">
    <div class="cal-event-name">事件名称</div>
    <div class="cal-event-desc">事件描述</div>
  </div>
  <div class="cal-event-tag">↻</div>
</div>
```

- `border-left-color` 和 `color` 由事件的玩家选择的颜色决定
- `cal-event-tag`（↻）仅在事件设为"每年重复"时出现
- `cal-event-desc` 仅在事件有描述时出现

### 支持的 HTML 标签

| 标签 | 说明 | 可点击 |
|------|------|--------|
| `<div>` | 通用容器 | 仅带 id 时可点击 |
| `<button>` | 按钮 | 是 |
| `<a>` | 链接 | 是 |
| `<span>` | 行内文本容器 | 否 |
| `<p>` | 段落 | 否 |
| `<h1>` - `<h6>` | 标题 | 否 |
| `<br>` | 换行 | 否 |
| `<hr>` | 分隔线 | 否 |
| `<img>` | 图片 | 否 |

### 支持的 HTML 属性

| 属性 | 说明 | 示例 |
|------|------|------|
| `class` | CSS 类名（多个用空格分隔） | `class="cal-day today"` |
| `id` | 元素唯一标识，用于点击事件 | `id="prev-month"` |
| `style` | 内联样式 | `style="color: #FF0000"` |

### 预定义的按钮 id

以下 id 在 Java 中有对应的点击事件处理，**自定义模板中如果包含这些 id 的按钮，点击会触发对应功能**：

| id | 功能 |
|----|------|
| `prev-month` | 上一月 |
| `next-month` | 下一月 |
| `open-client-config` | 打开客户端配置界面 |
| `open-calendar-config` | 打开历法配置界面 |
| `open-events` | 打开事件管理界面 |

**日期格 id**：`day-<数字>`（如 `day-1`），点击日期格可选中该日期查看事件。

### HTML 实体支持

模板中可以使用以下 HTML 实体：

| 类型 | 格式 | 示例 |
|------|------|------|
| 命名实体 | `&name;` | `&amp;` `&lt;` `&gt;` `&middot;` `&times;` |
| 十进制实体 | `&#NNN;` | `&#9664;`（◀） `&#9654;`（▶） `&#128197;`（📅） |
| 十六进制实体 | `&#xNN;` | `&#x25C6;`（◆） `&#x21BB;`（↻） |

### 自定义 HTML 模板注意事项

1. **必须保留所有 `{{variable}}` 占位符**，否则界面内容无法填充
2. **必须保留预定义按钮 id**（如 `prev-month`、`next-month`），否则导航功能失效
3. **class 名必须与 CSS 中一致**，否则样式无法应用
4. 可以添加新的 `<div>`、`<span>` 等元素和新的 class，但需在 CSS 中定义对应样式
5. 不支持 `<script>`、`<input>`、`<form>` 等交互标签
6. 不支持 `onclick` 等事件属性，点击事件通过 id 在 Java 端处理
7. 模板中的文本会自动转义，防止 XSS

### 自定义 HTML 示例

在标题区添加一个装饰性副标题：

```html
<div class="cal-root">
  <div class="cal-header">
    <button id="prev-month" class="cal-nav-btn">&#9664;</button>
    <div class="cal-title-wrap">
      <div class="cal-era">{{era_name}}</div>
      <div class="cal-title">{{year}}{{year_label}} {{month_name}}</div>
      <div class="cal-subtitle">{{month_events}}事件 &middot; {{today_weekday}}</div>
      <div class="cal-custom-badge">🌙 自定义文字</div>
    </div>
    <button id="next-month" class="cal-nav-btn">&#9654;</button>
  </div>
  <!-- ... 其余部分不变 ... -->
</div>
```

然后在 CSS 中添加：
```css
.cal-custom-badge {
    font-size: __ERA_FONT__px;
    color: #FF6B6B;
    text-align: center;
}
```

---

## 完整示例

### 示例 1：霓虹风格

`styles.json`:
```json
{
  "styles": [
    {
      "id": "neon",
      "name": "霓虹",
      "description": "赛博朋克霓虹风",
      "file": "styles/neon.css",
      "builtin": false,
      "hud": {
        "shadow": "#FF00FF28",
        "body": "#0F0F23E8",
        "decor": "#FF00FFFF",
        "border": "#00FFFF1A",
        "textPrimary": "#00FFFFFFF",
        "textSecondary": "#FF00FF80",
        "textEvent": "#FFFF00FF"
      }
    }
  ]
}
```

`styles/neon.css`:
```css
.cal-root {
    display: flex;
    flex-direction: column;
    padding: __ROOT_PAD__px;
    background: linear-gradient(to bottom, #0F0F23F2, #1A0A2EF2);
    border-radius: 12px;
    box-shadow: 0 0 30px rgba(255,0,255,0.3);
    color: #00FFFF;
    gap: __GAP__px;
}
.cal-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: __HEADER_PAD__px;
    background: linear-gradient(to right, #1A0A2EF2, #0F0F23F2);
    border-radius: 8px;
    border-width: 1px;
    border-color: #FF00FF60;
}
.cal-nav-btn {
    width: __NAV_BTN__px;
    height: __NAV_BTN__px;
    padding: 0;
    background: linear-gradient(to bottom, #FF00FF40, #00FFFF20);
    border-width: 1px;
    border-color: #FF00FF;
    border-radius: 6px;
    color: #00FFFF;
    font-size: __TITLE_FONT__px;
    text-align: center;
    box-shadow: 0 0 8px rgba(255,0,255,0.4);
}
.cal-era {
    font-size: __ERA_FONT__px;
    color: #FF00FF;
}
.cal-title {
    font-size: __TITLE_FONT__px;
    color: #00FFFF;
}
.cal-subtitle {
    font-size: __SUBTITLE_FONT__px;
    color: #FF00FF80;
}
.cal-week-row {
    display: grid;
    grid-template-columns: repeat(7, 1fr);
    gap: __DAY_GAP__px;
}
.cal-week-cell {
    text-align: center;
    padding: __WEEK_PAD__px 0;
    font-size: __WEEK_FONT__px;
    color: #00FFFF;
    background: #FF00FF15;
    border-radius: 4px;
    border-width: 1px;
    border-color: #FF00FF30;
}
.cal-week-cell.sun { color: #FF4444; }
.cal-week-cell.sat { color: #44FF44; }
.cal-grid {
    display: grid;
    grid-template-columns: repeat(7, 1fr);
    gap: __DAY_GAP__px;
}
.cal-day {
    height: __DAY_H__px;
    padding: __DAY_PAD__px;
    background: #0F0F2380;
    border-radius: 6px;
    border-width: 1px;
    border-color: #00FFFF30;
}
.cal-day.empty {
    background-color: transparent;
    border-color: transparent;
}
.cal-day.future {
    opacity: 0.4;
}
.cal-day.today {
    border-width: 2px;
    border-color: #00FFFF;
    background: #00FFFF20;
    box-shadow: 0 0 12px rgba(0,255,255,0.4);
}
.cal-day.has-event {
    border-color: #FFFF0080;
    background: #FFFF0010;
}
.cal-day.multi-event {
    border-color: #FF00FF80;
    background: #FF00FF10;
}
.cal-day-head {
    display: flex;
    justify-content: center;
    align-items: center;
    position: relative;
}
.cal-day-num {
    font-size: __DAY_NUM_FONT__px;
    color: #00FFFF;
    text-align: center;
    width: 100%;
}
.cal-day.today .cal-day-num {
    color: #FFFFFF;
}
.cal-day-dot {
    position: absolute;
    top: 0;
    right: 2px;
    width: 5px;
    height: 5px;
    background-color: #FFFF00;
    border-radius: 3px;
}
.cal-day-event {
    font-size: __DAY_EVENT_FONT__px;
    color: #FFFF00;
}
.cal-day-count {
    font-size: __DAY_EVENT_FONT__px;
    color: #FF00FF;
}
.cal-events-panel {
    padding: __EVENT_ROW_PAD__px;
    background: #0F0F2390;
    border-radius: 8px;
    border-width: 1px;
    border-color: #FF00FF40;
    height: __EVENTS_H__px;
}
.cal-panel-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 4px;
    padding-bottom: 4px;
    border-width: 1px;
    border-color: #FF00FF30;
}
.cal-panel-title {
    font-size: __EVENT_FONT__px;
    color: #00FFFF;
}
.cal-panel-stat {
    font-size: __ERA_FONT__px;
    color: #FF00FF;
}
.cal-no-event {
    text-align: center;
    padding: 6px;
    font-size: __ERA_FONT__px;
    color: #00FFFF60;
}
.cal-event-row {
    display: flex;
    align-items: center;
    padding: __EVENT_ROW_PAD__px;
    margin-bottom: 3px;
    background: #FF00FF10;
    border-radius: 4px;
    border-width: 2px;
    border-color: #FFFF00;
    gap: 6px;
}
.cal-event-icon {
    font-size: __EVENT_FONT__px;
}
.cal-event-content {
    display: flex;
    flex-direction: column;
}
.cal-event-name {
    font-size: __EVENT_FONT__px;
    color: #00FFFF;
}
.cal-event-desc {
    font-size: __EVENT_DESC_FONT__px;
    color: #FF00FF80;
}
.cal-event-tag {
    font-size: __EVENT_FONT__px;
    color: #FFFF00;
}
.cal-bottom {
    display: flex;
    gap: __GAP__px;
    justify-content: center;
}
.cal-btn {
    width: __BTN__px;
    height: __BTN_H__px;
    padding: 0;
    background: linear-gradient(to bottom, #FF00FF30, #00FFFF20);
    border-width: 1px;
    border-color: #00FFFF;
    border-radius: 6px;
    box-shadow: 0 0 6px rgba(0,255,255,0.3);
    color: #00FFFF;
    font-size: __BTN_FONT__px;
    text-align: center;
    line-height: __BTN_H__px;
}
```

### 示例 2：覆盖内置样式

如果你想修改内置的"暗夜"样式，只需在资源包中使用相同的 id：

```json
{
  "styles": [
    {
      "id": "dark",
      "name": "暗夜(修改版)",
      "description": "我修改的暗夜样式",
      "file": "styles/my_dark.css",
      "builtin": false
    }
  ]
}
```

这样你的 `my_dark.css` 会替换模组自带的 `dark.css`，玩家选择"暗夜"时看到的是你的样式。

---

## 常见问题

### Q: 不写动态变量可以吗？

**不行**。`__DAY_H__px`、`__ROOT_PAD__px` 等变量由 Java 根据屏幕分辨率动态计算。如果写成固定值（如 `45px`），在不同分辨率下会布局错乱。**所有涉及尺寸的属性都必须使用变量**。

### Q: 可以只用部分变量吗？

可以，但不推荐。未使用的变量不会报错，但对应元素会使用默认值（可能是 0），导致该元素尺寸异常。

### Q: 可以添加新的 CSS 类吗？

可以添加类，但 HTML 模板是固定的，新类不会自动出现在 HTML 中。除非你同时通过资源包覆盖 `calendar_screen.html` 模板。

### Q: 可以覆盖 HTML 模板吗？

可以。在资源包中放置 `assets/calendarmod/templates/calendar_screen.html` 即可覆盖。详见 [HTML 模板参考](#html-模板参考) 章节。

### Q: 为什么我的样式没有出现？

检查以下几点：
1. `pack.mcmeta` 的 `pack_format` 是否为 `15`
2. 资源包是否在游戏中已启用
3. `styles.json` 中的 `file` 路径是否正确
4. CSS 文件是否存在于指定路径
5. `styles.json` 是否为有效的 JSON 格式
6. `id` 是否为空

### Q: 可以删除内置样式吗？

不能。资源包的 `styles.json` 会与模组自带的合并。你只能覆盖（同 id）或新增，不能删除内置样式。

### Q: 多个资源包都添加了样式会怎样？

所有资源包的 `styles.json` 都会被合并。同 id 的样式，高优先级资源包覆盖低优先级。不同 id 的样式全部保留。

### Q: 不写 hud 字段会怎样？

HUD 会使用默认的灰白半透明配色，不会影响日历主界面（主界面由 CSS 文件控制）。只有写了 `hud` 字段，HUD 才会跟随你的样式风格切换。

### Q: 覆盖内置样式时，hud 字段会字段级合并吗？

**不会**。如果你覆盖内置样式（同 `id`）且写了 `hud` 字段，你的 `hud` 配置会**完全替换**模组自带的 HUD 配色。如果你只写了一部分子字段（例如只写 `body` 和 `textPrimary`），未写的字段会回退到**默认灰白值**，而**不会**继承被覆盖样式的对应字段。建议覆盖时把 7 个字段都写全。

### Q: 为什么我的 HUD 颜色没生效？

检查以下几点：
1. `styles.json` 中 `hud` 对象拼写是否正确（小写）
2. 颜色字符串是否为合法 hex 格式（`#RRGGBB`、`#RRGGBBAA` 等）
3. alpha 值是否过低（如 `#00000000` 完全透明，看不到）
4. 当前选中的样式是否是你修改的那个（在客户端设置中确认）

---

## 许可证

LGPL-2.1
