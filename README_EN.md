# Calendar Mod

> Customizable Calendar System for Minecraft 1.20.1 Forge

## Introduction

**Calendar Mod** is a full-featured calendar mod built on the HtmlCraft API. It introduces a customizable calendar system to Minecraft, where each save has independent calendar data. Players can define era names, month counts, days per month, month names, weekday names, add special date events, and interact through a modern HTML/CSS rendered interface.

---

## Features

### Calendar System

| Feature | Description |
|---------|-------------|
| Per-Save Independent | Each save has its own calendar data, mutually unaffected |
| Custom Era | Customizable era names (e.g., "Star Era", "AD") |
| Custom Months | Configurable number of months per year and days per month |
| Custom Month Names | Independent names for each month |
| Custom Weekdays | Configurable weekday names and days per week |
| Year Offset | Support for year offset to adjust the starting year |
| Independent Time Tracking | Unaffected by `/time` command, tracks actual game time, monotonically increasing |

### Event System

| Feature | Description |
|---------|-------------|
| Date Events | Add events to any date (name, description, color) |
| Yearly Repeat | Support for yearly recurring events (fixed month/day) |
| Color Selection | 12 preset colors in a list selector, plus custom color input |
| Event Tags | Support for event tag categorization |
| Special Date Broadcast | Broadcasts to all players via ActionBar when special dates arrive (no chat spam) |
| HUD Display | HUD shows today's event information in real time |

### Interface System

| Feature | Description |
|---------|-------------|
| HTML/CSS Rendering | Calendar main interface rendered via HtmlCraft API with modern UI effects |
| Multiple Built-in Styles | 6 built-in CSS styles (Light Gray / Dark / Ocean / Forest / Mystic / Minimal) |
| Style Switching | Switch interface styles in client config, takes effect instantly |
| Resource Pack Extension | Add/replace styles via resource packs, auto-detected and merged |
| Full-Screen Adaptive | Dynamically calculates layout based on screen resolution |
| Draggable HUD | HUD supports mouse drag to reposition, position auto-saved |
| Cloth Config | Configuration interface via Cloth Config, integrated with Catalogue mod list |
| Localization | Supports both Chinese and English |

### Network Sync

| Feature | Description |
|---------|-------------|
| Data Sync | Server calendar config and event data auto-sync to clients |
| Auto Sync | Events modifications auto-sync to all online players |
| Login Sync | Players receive latest calendar data on login |
| Client Cache | Client caches calendar data to reduce network overhead |

---

## Requirements

| Dependency | Version |
|------------|---------|
| Minecraft | 1.20.1 |
| Forge | 47.x |
| Java | 17 |
| HtmlCraft API | 1.0.0-1.20.1forge |
| Cloth Config | 11.x (required, for config UI) |
| Catalogue | (optional, for mod list config button) |

---

## Installation

1. Place `htmlcraftapi-1.0.0-1.20.1forge.jar`, `calendarmod-1.0.0-1.20.1forge.jar`, and `cloth-config-11.x-forge.jar` into the `mods` folder
2. (Optional) Add Catalogue for a config button in the mod list
3. Launch the game

---

## Key Bindings

| Key | Function | Default |
|-----|----------|---------|
| Open Calendar | Opens the calendar main interface | `C` |
| Toggle HUD | Show/hide calendar HUD | `H` |

Keys can be customized in Options → Controls → Key Bindings.

---

## Commands

All commands use `/calendar` as prefix.

| Command | Permission | Description |
|---------|------------|-------------|
| `/calendar` | All players | Show command help |
| `/calendar today` | All players | View today's date and events |
| `/calendar list` | All players | List all events |
| `/calendar add <year> <month> <day> <name>` | OP(2) | Add an event |
| `/calendar remove <index>` | OP(2) | Remove event at specified index |
| `/calendar sync` | OP(2) | Manually sync calendar data to all players |
| `/calendar set yearOffset <offset>` | OP(2) | Set year offset |
| `/calendar set eraName <name>` | OP(2) | Set era name |

---

## Interface Overview

### Calendar Main Screen

Press `C` to open. Includes:
- **Header**: Era name, year, month navigation
- **Weekday Row**: Custom weekday names
- **Date Grid**: CSS Grid layout, today highlighted, dates with events marked
- **Events Panel**: Shows event list for selected date
- **Bottom Buttons**:
  - **Client Settings**: Opens Cloth Config (HUD, styles, etc.)
  - **Calendar Settings**: Opens calendar config (era, months, etc.)
  - **Add Event**: Opens event management screen

### Event Management Screen

- Date input (year/month/day)
- Event name and description
- Color selection list (12 presets + custom color input)
- Yearly repeat toggle
- Existing events list (supports deletion)

### HUD

- Shows current date and weekday
- Shows today's events (configurable)
- Light gray semi-transparent modern UI
- Draggable to reposition
- Position auto-saved to config file

### Client Config (Cloth Config)

| Config Option | Description |
|---------------|-------------|
| HUD Enabled | Whether to show calendar HUD |
| HUD X/Y Position | HUD position |
| HUD Right-Aligned | Whether HUD is positioned from the right |
| Show Today's Events | Whether HUD shows today's events |
| Show Event Description | Whether HUD shows event descriptions |
| Interface Style | Select calendar interface CSS style |
| Default Month Offset | Default month when opening calendar |
| Auto-Open on Login | Automatically open calendar on login |

---

## Style System

### Built-in Styles

| Style | Description |
|-------|-------------|
| Light Gray (Default) | Light gray semi-transparent modern style |
| Dark | Dark theme |
| Ocean | Blue tech style |
| Forest | Green natural style |
| Mystic | Purple mystic style |
| Minimal | Flat minimalist style |

### Adding Custom Styles via Resource Pack

Resource packs only need to provide their own `styles.json` (containing only new styles) and corresponding CSS files. The mod auto-merges and detects them:

```
resource_pack/
├── pack.mcmeta
└── assets/calendarmod/templates/
    ├── styles.json              ← Only contains new style entries
    └── styles/
        └── my_style.css         ← Custom CSS file
```

`styles.json` format:
```json
{
  "styles": [
    {
      "id": "my_style",
      "name": "My Custom",
      "description": "Resource pack custom style",
      "file": "styles/my_style.css",
      "builtin": false
    }
  ]
}
```

After enabling the resource pack, open Calendar → Client Settings → Interface Style dropdown to see the new style.

CSS files can use `__ROOT_PAD__`, `__GAP__`, and other double-underscore variables, dynamically replaced by Java with screen-adaptive pixel values.

For detailed resource pack creation guide, see [Resource Pack Guide](./docs/RESOURCEPACK_GUIDE_EN.md).

---

## Time System

- Each new save starts from **Year 1**
- Calendar time is based on **actual game time**, unaffected by `/time` command
- Even if other mods reset time, elapsed calendar time never goes backward
- 24000 ticks = 1 day
- Special dates broadcast to all players via ActionBar, once per day

---

## Project Structure

```
com.calendar.mod
├── CalendarMod.java              // Mod main class, key bindings, network registration
├── calendar/
│   ├── CalendarConfig.java       // Calendar config data model
│   ├── CalendarDate.java         // Date data model
│   ├── CalendarSystem.java       // Calendar system interface
│   ├── ConfigurableCalendar.java // Configurable calendar implementation
│   └── DefaultCalendar.java      // Default calendar implementation
├── client/
│   ├── CalendarClientConfig.java // Client config (Cloth Config)
│   ├── CalendarConfigScreen.java // Calendar config screen
│   ├── CalendarEventScreen.java  // Event management screen
│   ├── CalendarHud.java          // HUD rendering
│   ├── CalendarScreen.java       // Calendar main screen (HTML/CSS)
│   ├── HudRenderSubscriber.java  // HUD render event subscriber
│   └── StyleManager.java         // Style manager
├── command/
│   └── CalendarCommand.java      // /calendar command
├── data/
│   ├── CalendarEvent.java        // Event data model
│   └── CalendarSavedData.java    // Save data persistence
├── network/
│   ├── CalendarClientCache.java  // Client data cache
│   ├── CalendarConfigPacket.java // Calendar config sync packet
│   ├── CalendarEventPacket.java  // Event edit packet
│   └── CalendarSyncPacket.java   // Calendar data sync packet
└── server/
    └── AutoSyncScheduler.java    // Auto sync scheduler
```

---

## Resource Files

```
assets/calendarmod/
├── lang/
│   ├── zh_cn.json                // Chinese language file
│   └── en_us.json                // English language file
├── templates/
│   ├── calendar_screen.html      // Calendar screen HTML template
│   ├── calendar_screen.css       // Default CSS style
│   ├── styles.json               // Style metadata
│   └── styles/
│       ├── dark.css              // Dark
│       ├── ocean.css             // Ocean
│       ├── forest.css            // Forest
│       ├── mystic.css            // Mystic
│       └── minimal.css           // Minimal
└── icon.png                      // Mod icon
```

---

## Addon Development

The mod provides `CalendarSystem` interface and `CalendarSavedData` API. Addon mods can:
- Implement custom calendar systems (implement `CalendarSystem` interface)
- Read/modify save calendar data (via `CalendarSavedData.get(level)`)
- Listen for date change events
- Register custom event types

---

## License

LGPL-2.1

## Author

Yifei

## Dependencies

- [HtmlCraft API](./HtmlCraftAPI/README_EN.md) — HTML/CSS Rendering Engine
