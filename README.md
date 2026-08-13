# Calendar Mod

> Minecraft 1.20.1 Forge / Fabric 及 26.2 Fabric 可自定义历法系统

## 简介

**Calendar Mod** 是一个基于 HtmlCraft API 构建的全功能日历模组。它为 Minecraft 引入了可自定义的历法系统，每个存档拥有独立的日历数据。玩家可以自定义纪元名称、月份数量、每月天数、月份名称、星期名称，添加特殊日期事件，并通过现代化的 HTML/CSS 渲染界面进行交互。

---

## 特性

### 历法系统

| 特性 | 说明 |
|------|------|
| 存档独立 | 每个存档拥有独立的日历数据，互不影响 |
| 自定义纪元 | 可自定义纪元名称（如"星历"、"公元"等） |
| 自定义月份 | 可配置每年的月份数量和每月天数 |
| 自定义月份名 | 可为每个月份设置独立名称 |
| 自定义星期 | 可配置星期名称和一周天数 |
| 年份偏移 | 支持设置年份偏移量，调整起始年份 |
| 独立时间追踪 | 不受 `/time` 指令影响，记录实际游戏时长，时间单调递增 |

### 事件系统

| 特性 | 说明 |
|------|------|
| 日期事件 | 可为任意日期添加事件（名称、描述、颜色） |
| 每年重复 | 支持设置事件为每年重复（固定月日） |
| 颜色选择 | 提供 12 种预设颜色列表选择，支持自定义颜色 |
| 事件标签 | 支持为事件添加标签分类 |
| 特殊日期广播 | 特殊日期到来时通过 ActionBar 广播全体玩家（不刷屏聊天栏） |
| HUD 显示 | HUD 实时显示今日事件信息 |

### 界面系统

| 特性 | 说明 |
|------|------|
| HTML/CSS 渲染 | 日历主界面基于 HtmlCraft API 渲染，支持现代化 UI 效果 |
| 多套内置样式 | 提供 6 套内置 CSS 样式（灰白/暗夜/海洋/森林/幻境/极简） |
| 样式切换 | 可在客户端配置中切换界面样式，即时生效 |
| 资源包扩展 | 支持通过资源包新增/替换样式，自动识别合并 |
| 全屏自适应 | 根据屏幕分辨率动态计算布局，兼容不同分辨率 |
| 可拖拽 HUD | HUD 支持鼠标拖拽调整位置，位置自动保存 |
| Cloth Config | 使用 Cloth Config 提供配置界面，Forge 版联动 Catalogue，Fabric 版联动 ModMenu |
| 本地化 | 支持中英文双语 |

### 网络同步

| 特性 | 说明 |
|------|------|
| 数据同步 | 服务端历法配置和事件数据自动同步到客户端 |
| 自动同步 | 事件修改后自动同步给所有在线玩家 |
| 登录同步 | 玩家登录时自动接收最新日历数据 |
| 客户端缓存 | 客户端缓存日历数据，减少网络开销 |

---

## 环境要求

本模组提供三个版本，按所用加载器和 Minecraft 版本选择对应版本即可。

### Forge 版（1.20.1）

| 依赖 | 版本 |
|------|------|
| Minecraft | 1.20.1 |
| Forge | 47.x |
| Java | 17 |
| HtmlCraft API | 1.0.0-1.20.1forge |
| Cloth Config | 11.x（必需，用于配置界面） |
| Catalogue | （可选，用于模组列表配置按钮） |

### Fabric 版（1.20.1）

| 依赖 | 版本 |
|------|------|
| Minecraft | 1.20.1 |
| Fabric Loader | >=0.19.3 |
| Fabric API | 0.92.11+1.20.1 |
| Java | 17 |
| HtmlCraft API | 1.0.0-1.20.1fabric |
| Cloth Config | 11.x（必需，用于配置界面） |
| ModMenu | 7.x（可选，用于模组列表配置按钮） |

### Fabric 版（26.2）

| 依赖 | 版本 |
|------|------|
| Minecraft | 26.2 |
| Fabric Loader | >=0.19.3 |
| Fabric API | 0.157.0+26.2 |
| Java | 25 |
| HtmlCraft API | 1.0.0-26.2fabric（必需） |
| Cloth Config | 26.2.155（必需，配置界面） |
| ModMenu | 20.0.x（可选，模组列表配置按钮） |

---

## 安装

### Forge 版（1.20.1）

1. 将 `htmlcraftapi-1.0.0-1.20.1forge.jar`、`calendarmod-1.0.0-1.20.1forge.jar` 和 `cloth-config-11.x-forge.jar` 放入 `mods` 文件夹
2. （可选）放入 Catalogue 以在模组列表中显示配置按钮
3. 启动游戏

### Fabric 版（1.20.1）

1. 安装 Fabric Loader，确保已放入 Fabric API
2. 将 `htmlcraftapi-1.0.0-1.20.1fabric.jar`、`calendarmod-1.0.0-1.20.1fabric.jar` 和 `cloth-config-11.x-fabric.jar` 放入 `mods` 文件夹
3. （可选）放入 ModMenu 以在模组列表中显示配置按钮
4. 启动游戏

### Fabric 版（26.2）

1. 安装 Fabric Loader，确保已放入 Fabric API
2. 将 `htmlcraftapi-1.0.0-26.2fabric.jar`、`calendarmod-1.0.0-26.2fabric.jar` 和 `cloth-config-26.2.155.jar` 放入 `mods` 文件夹
3. （可选）放入 `modmenu-20.0.x.jar` 以在模组列表中显示配置按钮
4. 启动游戏

---

## 按键绑定

| 按键 | 功能 | 默认键 |
|------|------|--------|
| 打开日历界面 | 打开日历主界面 | `C` |
| 切换 HUD | 显示/隐藏日历 HUD | `H` |

按键可在游戏设置 → 按键绑定中自定义。

---

## 命令

所有命令以 `/calendar` 为前缀。

| 命令 | 权限 | 说明 |
|------|------|------|
| `/calendar` | 所有玩家 | 显示命令帮助 |
| `/calendar today` | 所有玩家 | 查看今日日期和事件 |
| `/calendar list` | 所有玩家 | 列出所有事件 |
| `/calendar add <年> <月> <日> <名称>` | OP(2) | 添加事件 |
| `/calendar remove <索引>` | OP(2) | 删除指定索引的事件 |
| `/calendar sync` | OP(2) | 手动同步日历数据给所有玩家 |
| `/calendar set yearOffset <偏移>` | OP(2) | 设置年份偏移量 |
| `/calendar set eraName <名称>` | OP(2) | 设置纪元名称 |

---

## 界面说明

### 日历主界面

按 `C` 键打开。包含：
- **标题栏**：纪元名称、年份、月份导航
- **星期行**：自定义星期名称
- **日期网格**：CSS Grid 布局，今日高亮，有事件的日期标记
- **事件面板**：显示选中日期的事件列表
- **底部按钮**：
  - **客户端设置**：打开 Cloth Config 界面（HUD、样式等）
  - **历法设置**：打开历法配置界面（纪元、月份等）
  - **添加事件**：打开事件管理界面

### 事件管理界面

- 日期输入（年/月/日）
- 事件名称和描述
- 颜色选择列表（12 种预设 + 自定义颜色输入）
- 每年重复开关
- 已有事件列表（支持删除）

### HUD

- 显示当前日期和星期
- 显示今日事件（可配置）
- 灰白半透明现代化 UI 质感
- 可拖拽移动位置
- 位置自动保存到配置文件

### 客户端配置（Cloth Config）

| 配置项 | 说明 |
|--------|------|
| HUD 启用 | 是否显示日历 HUD |
| HUD X/Y 坐标 | HUD 位置 |
| HUD 右对齐 | HUD 是否从右侧定位 |
| 显示今日事件 | HUD 是否显示今日事件 |
| 显示事件描述 | HUD 是否显示事件描述 |
| 界面样式 | 选择日历界面 CSS 样式 |
| 默认月份偏移 | 打开日历时的默认月份 |
| 登录自动打开 | 登录时自动打开日历 |

---

## 样式系统

### 内置样式

| 样式 | 说明 |
|------|------|
| 灰白(默认) | 灰白半透明现代化风格 |
| 暗夜 | 深色暗色调 |
| 海洋 | 蓝色科技风 |
| 森林 | 绿色自然风 |
| 幻境 | 紫色神秘风 |
| 极简 | 扁平简约风 |

### 通过资源包添加自定义样式

资源包只需提供自己的 `styles.json`（只包含新增样式）和对应 CSS 文件，模组会自动合并识别：

```
资源包/
├── pack.mcmeta
└── assets/calendarmod/templates/
    ├── styles.json              ← 只包含新增的样式条目
    └── styles/
        └── my_style.css         ← 自定义 CSS 文件
```

`styles.json` 格式：
```json
{
  "styles": [
    {
      "id": "my_style",
      "name": "我的自定义",
      "description": "资源包新增样式",
      "file": "styles/my_style.css",
      "builtin": false
    }
  ]
}
```

启用资源包后，打开日历 → 客户端设置 → 界面样式下拉框即可看到新增样式。

CSS 文件中可使用 `__ROOT_PAD__`、`__GAP__` 等双下划线变量，由 Java 动态替换为屏幕适配的像素值。

### 资源包制作工具与教程

模组提供了带 GUI 界面的资源包生成器和详细教程，帮助快速创建自定义样式：

| 资源 | 说明 |
|------|------|
| 📦 资源包生成器 | PySide6 GUI 工具，可视化编辑样式、配置 HUD 颜色、导出 ZIP |
| 📖 资源包制作教程 | 中英文详细文档，涵盖所有字段、CSS 属性、HTML 模板 |
| 🛠️ 工具使用教程 | 生成器工具的完整使用说明 |

👉 **工具与教程目录**：[docs/](https://github.com/19136644525lxy/Calendar-Mod/tree/main/docs)

快速启动：
```bash
cd docs
pip install -r requirements.txt
python resource_pack_generator.py
```

或直接双击 `docs/启动资源包生成器.bat`（Windows）。

---

## 时间系统说明

- 每个新存档从**第 1 年**开始计时
- 日历时间基于**实际游戏时长**累计，不受 `/time` 指令影响
- 即使其他模组重置时间，已过去的日历时间不会倒退
- 每 24000 ticks = 1 天
- 特殊日期到来时通过 ActionBar 广播全体玩家，每天只广播一次

---

## 项目结构

```
com.calendar.mod
├── CalendarMod.java              // 模组主类、按键绑定、网络注册
├── calendar/
│   ├── CalendarConfig.java       // 历法配置数据模型
│   ├── CalendarDate.java         // 日期数据模型
│   ├── CalendarSystem.java       // 历法系统接口
│   ├── ConfigurableCalendar.java // 可配置历法实现
│   └── DefaultCalendar.java      // 默认历法实现
├── client/
│   ├── CalendarClientConfig.java // 客户端配置（Cloth Config）
│   ├── CalendarConfigScreen.java // 历法配置界面
│   ├── CalendarEventScreen.java  // 事件管理界面
│   ├── CalendarHud.java          // HUD 渲染
│   ├── CalendarScreen.java       // 日历主界面（HTML/CSS）
│   ├── HudRenderSubscriber.java  // HUD 渲染事件订阅
│   └── StyleManager.java         // 样式管理器
├── command/
│   └── CalendarCommand.java      // /calendar 命令
├── data/
│   ├── CalendarEvent.java        // 事件数据模型
│   └── CalendarSavedData.java    // 存档数据持久化
├── network/
│   ├── CalendarClientCache.java  // 客户端数据缓存
│   ├── CalendarConfigPacket.java // 历法配置同步包
│   ├── CalendarEventPacket.java  // 事件编辑包
│   └── CalendarSyncPacket.java   // 日历数据同步包
└── server/
    └── AutoSyncScheduler.java    // 自动同步调度器
```

---

## 资源文件

```
assets/calendarmod/
├── lang/
│   ├── zh_cn.json                // 中文语言文件
│   └── en_us.json                // 英文语言文件
├── templates/
│   ├── calendar_screen.html      // 日历界面 HTML 模板
│   ├── calendar_screen.css       // 默认 CSS 样式
│   ├── styles.json               // 样式元数据
│   └── styles/
│       ├── dark.css              // 暗夜
│       ├── ocean.css             // 海洋
│       ├── forest.css            // 森林
│       ├── mystic.css            // 幻境
│       └── minimal.css           // 极简
└── icon.png                      // 模组图标
```

---

## 附属模组开发

主模组提供 `CalendarSystem` 接口和 `CalendarSavedData` API，附属模组可：
- 实现自定义历法系统（实现 `CalendarSystem` 接口）
- 读取/修改存档日历数据（通过 `CalendarSavedData.get(level)`）
- 监听日期变化事件
- 注册自定义事件类型

---

## 许可证

LGPL-2.1

## 作者

Yifei

## 前置模组

- [HtmlCraft API](https://github.com/19136644525lxy/HtmlCraft-API) — HTML/CSS 渲染引擎
