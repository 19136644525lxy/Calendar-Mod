# Calendar Mod Resource Pack Guide

> This guide details how to customize the calendar interface through resource packs.

---

## Table of Contents

1. [Quick Start](#quick-start)
2. [Resource Pack Structure](#resource-pack-structure)
3. [pack.mcmeta](#packmcmeta)
4. [styles.json Reference](#stylesjson-reference)
5. [HUD Color Configuration](#hud-color-configuration)
6. [CSS Variables Reference](#css-variables-reference)
7. [CSS Class Reference](#css-class-reference)
8. [Supported CSS Properties](#supported-css-properties)
9. [Color Formats](#color-formats)
10. [HTML Template Reference](#html-template-reference)
11. [Complete Example](#complete-example)
12. [FAQ](#faq)

---

## Quick Start

Create a custom style in 3 steps:

1. Create a resource pack folder with `pack.mcmeta`
2. Place your CSS file under `assets/calendarmod/templates/styles/`
3. Declare your style in `assets/calendarmod/templates/styles.json`

Enable the resource pack in-game, then go to Calendar → Client Settings → Interface Style to see your new style.

---

## Resource Pack Structure

```
my_calendar_style/
├── pack.mcmeta
└── assets/
    └── calendarmod/
        └── templates/
            ├── styles.json                    ← Declare new/overridden styles
            └── styles/
                ├── my_style.css               ← Your custom CSS
                ├── another_style.css          ← You can add multiple
                └── ...
```

### Two Usage Modes

| Mode | Description |
|------|-------------|
| **Add new style** | List only your new styles in `styles.json`; the mod auto-merges with the 6 built-in styles |
| **Override style** | Use the same `id` as a built-in style (e.g., `dark`); higher-priority packs override lower ones |

---

## pack.mcmeta

```json
{
  "pack": {
    "pack_format": 15,
    "description": "My Custom Calendar Style"
  }
}
```

| Field | Description |
|-------|-------------|
| `pack_format` | `15` for Minecraft 1.20.1 |
| `description` | Pack description shown in the resource pack selection screen |

---

## styles.json Reference

This is the metadata file that tells the mod which styles are available.

### Full Format

```json
{
  "styles": [
    {
      "id": "my_style",
      "name": "My Style",
      "description": "A custom style",
      "file": "styles/my_style.css",
      "builtin": false
    }
  ]
}
```

### Field Reference

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `id` | String | Yes | Unique style identifier, lowercase + underscores (e.g., `my_style`). Same id as built-in = override |
| `name` | String | No | Display name shown in the config dropdown. Defaults to id |
| `description` | String | No | Style description, for reference only |
| `file` | String | Yes | CSS file path relative to `templates/`, e.g., `styles/my_style.css` |
| `builtin` | Boolean | No | Whether this is a built-in style. Resource pack styles should set `false`. Default `true` |
| `hud` | Object | No | HUD color configuration object, customizes the small calendar HUD colors for this style. See [HUD Color Configuration](#hud-color-configuration) section. Omit to use default gray-white colors |

### id Naming Rules

- Only lowercase letters, numbers, and underscores
- Cannot start with a number
- **Examples**: `my_style`, `neon_blue`, `retro_80s`

### Built-in ids (for overriding)

| id | Style |
|----|-------|
| `default` | Light Gray (Default) |
| `dark` | Dark Night |
| `ocean` | Ocean |
| `forest` | Forest |
| `mystic` | Mystic |
| `minimal` | Minimal |

### Multiple Styles

One `styles.json` can declare multiple styles:

```json
{
  "styles": [
    {
      "id": "neon",
      "name": "Neon",
      "description": "Cyberpunk neon style",
      "file": "styles/neon.css",
      "builtin": false
    },
    {
      "id": "sakura",
      "name": "Sakura",
      "description": "Pink cherry blossom style",
      "file": "styles/sakura.css",
      "builtin": false
    }
  ]
}
```

---

## HUD Color Configuration

The calendar HUD (the small calendar box at the top-right of the screen) automatically switches its color scheme to match the player's selected style. Add an optional `hud` field to a style entry in `styles.json` to customize the HUD colors for that style.

### Basic Syntax

Add a `hud` sub-object to the style object:

```json
{
  "id": "neon",
  "name": "Neon",
  "description": "Cyberpunk neon style",
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

### Color Field Reference

| Field | Purpose | Rendered On |
|-------|---------|-------------|
| `shadow` | Shadow color (multi-layer outward gradient) | 4 outward-expanded rectangles around HUD |
| `body` | Main background color | HUD main background rectangle |
| `decor` | Top decoration strip color | 3px-tall decoration band at HUD top |
| `border` | Thin border color | 1px rounded border around HUD body |
| `textPrimary` | Primary text color (line 1: era year month) | HUD first line |
| `textSecondary` | Secondary text color (line 2: day weekday) | HUD second line and beyond (non-event lines) |
| `textEvent` | Event text color (◆ prefixed event lines) | Event lines starting with ◆ in HUD |

### Color Formats

HUD color fields support the following hex formats, **with alpha channel at the end** (consistent with `#RRGGBBAA` in CSS):

| Format | Example | Description |
|--------|---------|-------------|
| `#RRGGBB` | `#FF5733` | 6-digit hex, fully opaque |
| `#RRGGBBAA` | `#FF573380` | 8-digit hex, AA=alpha (00=transparent, FF=opaque) |
| `#RGB` | `#F53` | 3-digit shorthand, fully opaque |
| `#RGBA` | `#F538` | 4-digit shorthand, AA at end |
| `0xAARRGGBB` | `0x80FF5733` | Java-style, AA at start (not recommended, prefer `#` prefix) |

> **Note**: HUD color fields **do not support** the `rgba()` function or CSS variable placeholders (like `__XXX__`). These colors are parsed directly by Java and used as integers for `GuiGraphics` drawing, independent of the CSS rendering engine.

### Field Omission Rules

The `hud` field and all its sub-fields are **optional**:

- **Omitting `hud` entirely**: Uses default gray-white colors
- **Writing only some sub-fields**: Missing fields use default values (same as the `default` style's corresponding field)

Default values reference:

| Field | Default | Description |
|-------|---------|-------------|
| `shadow` | `#00000028` | 25% opacity black |
| `body` | `#F7F7F8E8` | 91% opacity light gray-white |
| `decor` | `#E4E7ECFF` | Light gray |
| `border` | `#0000001A` | 10% opacity black |
| `textPrimary` | `#1E293BFF` | Dark gray-blue |
| `textSecondary` | `#475569FF` | Medium gray |
| `textEvent` | `#B45309FF` | Amber |

### Built-in Style HUD Colors

All 6 built-in styles have pre-configured HUD colors, which you can reference or override:

| Style | body | decor | textPrimary | textEvent |
|-------|------|-------|-------------|-----------|
| Light Gray (Default) | `#F7F7F8E8` | `#E4E7ECFF` | `#1E293BFF` | `#B45309FF` |
| Dark Night | `#1E1E24E8` | `#2A2A32FF` | `#FFFFFFFF` | `#FFB74DFF` |
| Ocean | `#E3F2FDE8` | `#1976D2FF` | `#0D47A1FF` | `#E65100FF` |
| Forest | `#E8F5E9E8` | `#2E7D32FF` | `#1B5E20FF` | `#E65100FF` |
| Mystic | `#F3E5F5E8` | `#6A1B9AFF` | `#4A148CFF` | `#E65100FF` |
| Minimal | `#FAFAFAE8` | `#EEEEEEFF` | `#212121FF` | `#E65100FF` |

### Sync Mechanism Notes

- HUD colors are **fully synced** with the calendar interface style: when a player switches styles in client settings, HUD colors take effect **immediately** without restarting the game
- When overriding a built-in style (same `id`): the resource pack's `hud` config **completely replaces** the mod's built-in HUD colors (no field-level merging)
- If a resource pack style omits the `hud` field: uses default gray-white HUD colors, does not inherit the overridden style's HUD colors

### Complete Example: Custom HUD Colors

```json
{
  "styles": [
    {
      "id": "neon",
      "name": "Neon",
      "description": "Cyberpunk neon style",
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

This color scheme makes the HUD display: dark purple-black translucent background + magenta decoration strip + cyan primary text + magenta secondary text + yellow event text, visually unified with the neon-style CSS.

---

## CSS Variables Reference

CSS files **must use** the following variable placeholders. They are dynamically replaced with pixel values at runtime by Java, calculated based on screen resolution.

**If you omit a variable, the corresponding CSS property will become an invalid value like `__XXX__px`, causing layout issues.**

### All Dynamic Variables

| Variable | Description | Formula | Minimum |
|----------|-------------|---------|---------|
| `__ROOT_PAD__` | Root container padding | `screenH / 30` | 6 |
| `__GAP__` | Element spacing | `screenH / 50` | 4 |
| `__HEADER_PAD__` | Header padding | `screenH / 35` | 6 |
| `__NAV_BTN__` | Navigation button size | `screenH / 12` | 28 |
| `__TITLE_FONT__` | Title font size | `screenH / 22` | 14 |
| `__ERA_FONT__` | Era font size | `screenH / 36` | 9 |
| `__SUBTITLE_FONT__` | Subtitle font size | `screenH / 36` | 9 |
| `__WEEK_PAD__` | Weekday cell padding | `screenH / 60` | 3 |
| `__WEEK_FONT__` | Weekday font size | `screenH / 30` | 10 |
| `__DAY_GAP__` | Day cell spacing | `screenH / 60` | 3 |
| `__DAY_H__` | Day cell height | Dynamic remaining space / 6 | 20 |
| `__DAY_PAD__` | Day cell padding | `dayH / 10` | 2 |
| `__DAY_NUM_FONT__` | Day number font size | `dayH / 2` | 11 |
| `__DAY_EVENT_FONT__` | Day event text font size | `dayH / 4` | 8 |
| `__BTN__` | Bottom button width | `(screenW - rootPad*2 - gap*2) / 3` | 50 |
| `__BTN_H__` | Bottom button height | `screenH / 16` | 18 |
| `__BTN_FONT__` | Bottom button font size | `screenH / 32` | 9 |
| `__EVENTS_H__` | Events panel height | `screenH / 5` | 50 |
| `__EVENT_ROW_PAD__` | Event row padding | `eventsPanelH / 20` | 3 |
| `__EVENT_FONT__` | Event name font size | `eventsPanelH / 14` | 10 |
| `__EVENT_DESC_FONT__` | Event description font size | `eventsPanelH / 18` | 8 |

### Usage

Write the variable name directly in CSS followed by `px`:

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

**Note**: Variable replacement is plain text replacement, so `__DAY_H__px` becomes `45px`. Do not add spaces around variable names.

### Properties That Don't Need Variables

These properties don't involve dynamic sizing and can use fixed pixel values:

- `border-radius`
- `border-width`
- `margin`
- `opacity`
- `gap` (can use fixed value if you don't care about adaptive sizing)
- All color values

---

## CSS Class Reference

These are all classes used in the HTML template. Your CSS **must cover these classes** for correct rendering.

### Structure Classes

| Class | Description | HTML Structure |
|-------|-------------|----------------|
| `.cal-root` | Root container, holds everything | `<div class="cal-root">` |
| `.cal-header` | Top header area | `<div class="cal-header">` |
| `.cal-nav-btn` | Month navigation buttons (◀ ▶) | `<button class="cal-nav-btn">` |
| `.cal-title-wrap` | Title text container | `<div class="cal-title-wrap">` |
| `.cal-era` | Era name | `<div class="cal-era">` |
| `.cal-title` | Year-month title | `<div class="cal-title">` |
| `.cal-subtitle` | Subtitle (event count + weekday) | `<div class="cal-subtitle">` |
| `.cal-week-row` | Weekday header row container | `<div class="cal-week-row">` |
| `.cal-week-cell` | Weekday cell | `<div class="cal-week-cell">` |
| `.cal-grid` | Date grid container | `<div class="cal-grid">` |
| `.cal-day` | Date cell | `<div class="cal-day">` |
| `.cal-day-head` | Day cell header (number + dot) | `<div class="cal-day-head">` |
| `.cal-day-num` | Day number | `<div class="cal-day-num">` |
| `.cal-day-dot` | Event indicator dot | `<div class="cal-day-dot">` |
| `.cal-day-event` | Event name in day cell | `<div class="cal-day-event">` |
| `.cal-day-count` | Extra event count (+N) | `<div class="cal-day-count">` |
| `.cal-events-panel` | Today's events panel | `<div class="cal-events-panel">` |
| `.cal-panel-header` | Panel header row | `<div class="cal-panel-header">` |
| `.cal-panel-title` | Panel title | `<div class="cal-panel-title">` |
| `.cal-panel-stat` | Panel statistics | `<div class="cal-panel-stat">` |
| `.cal-no-event` | No events message | `<div class="cal-no-event">` |
| `.cal-event-row` | Event row | `<div class="cal-event-row">` |
| `.cal-event-icon` | Event icon (◆) | `<div class="cal-event-icon">` |
| `.cal-event-content` | Event content container | `<div class="cal-event-content">` |
| `.cal-event-name` | Event name | `<div class="cal-event-name">` |
| `.cal-event-desc` | Event description | `<div class="cal-event-desc">` |
| `.cal-event-tag` | Event tag (↻ yearly repeat) | `<div class="cal-event-tag">` |
| `.cal-bottom` | Bottom button bar | `<div class="cal-bottom">` |
| `.cal-btn` | Bottom button | `<button class="cal-btn">` |

### State Modifier Classes

These classes are **added on top of** base classes to mark special states. Use `.class.state` selectors in CSS.

| Modifier | Applied To | Description |
|----------|------------|-------------|
| `.empty` | `.cal-day` | Empty day cell (month start padding) |
| `.today` | `.cal-day` | Today |
| `.future` | `.cal-day` | Future date |
| `.has-event` | `.cal-day` | Date with events |
| `.multi-event` | `.cal-day` | Date with 2+ events |
| `.sun` | `.cal-week-cell` | Sunday (first) |
| `.sat` | `.cal-week-cell` | Saturday (last) |

**Example**:

```css
/* Normal day cell */
.cal-day { ... }

/* Today's cell */
.cal-day.today { ... }

/* Date with events */
.cal-day.has-event { ... }

/* Today with events */
.cal-day.today.has-event { ... }

/* Sunday */
.cal-week-cell.sun { ... }
```

---

## Supported CSS Properties

### Layout

| Property | Values | Example |
|----------|--------|---------|
| `display` | `block` / `flex` / `inline` / `grid` / `none` | `display: flex;` |
| `flex-direction` | `row` / `column` | `flex-direction: column;` |
| `justify-content` | `flex-start` / `center` / `flex-end` / `space-between` / `space-around` | `justify-content: center;` |
| `align-items` | `flex-start` / `center` / `flex-end` / `stretch` | `align-items: center;` |
| `gap` | Pixel value | `gap: 4px;` |
| `grid-template-columns` | `repeat(N, 1fr)` | `grid-template-columns: repeat(7, 1fr);` |

### Sizing

| Property | Values | Example |
|----------|--------|---------|
| `width` | Pixel value / `auto` | `width: 40px;` |
| `height` | Pixel value / `auto` | `height: __DAY_H__px;` |
| `padding` | Pixel value | `padding: 8px;` |
| `margin` | Pixel value | `margin-bottom: 4px;` |

### Visual Effects

| Property | Values | Example |
|----------|--------|---------|
| `background` | Solid color / gradient | See Color Formats below |
| `color` | Text color | `color: #FFFFFF;` |
| `border-width` | Pixel value | `border-width: 1px;` |
| `border-color` | Border color | `border-color: #FFFFFF20;` |
| `border-radius` | Pixel value | `border-radius: 10px;` |
| `box-shadow` | `offsetX offsetY blur spread color` | `box-shadow: 0 4px 12px rgba(0,0,0,0.2);` |
| `opacity` | `0.0` - `1.0` | `opacity: 0.5;` |

### Text

| Property | Values | Example |
|----------|--------|---------|
| `font-size` | Pixel value | `font-size: 14px;` |
| `text-align` | `left` / `center` / `right` | `text-align: center;` |
| `line-height` | Pixel value | `line-height: 30px;` |

### Position

| Property | Values | Example |
|----------|--------|---------|
| `position` | `static` / `relative` / `absolute` / `fixed` | `position: absolute;` |
| `top` / `right` / `bottom` / `left` | Pixel value | `top: 0; right: 2px;` |
| `z-index` | Integer | `z-index: 10;` |

### Overflow

| Property | Values | Example |
|----------|--------|---------|
| `overflow-x` | `visible` / `hidden` / `scroll` / `auto` | `overflow-x: hidden;` |
| `overflow-y` | `visible` / `hidden` / `scroll` / `auto` | `overflow-y: scroll;` |

---

## Color Formats

### Supported Formats

| Format | Example | Description |
|--------|---------|-------------|
| `#RRGGBB` | `#FF5733` | 6-digit hex, opaque |
| `#AARRGGBB` | `#80FF5733` | 8-digit hex, AA=alpha (00=transparent, FF=opaque) |
| `#RRGGBBAA` | `#FF573380` | 8-digit hex, AA at end |
| `rgba(r,g,b,a)` | `rgba(0,0,0,0.2)` | RGBA function, a is 0.0-1.0 |

### Gradients

```css
/* Linear gradient */
background: linear-gradient(to bottom, #1A1A2E, #16213E);
background: linear-gradient(to right, #F8F8F8FA, #F0F0F3FA);
background: linear-gradient(to bottom, #2A2A32F2, #22222AF2);
```

**Direction keywords**: `to bottom`, `to top`, `to left`, `to right`

---

## HTML Template Reference

Override `assets/calendarmod/templates/calendar_screen.html` via resource pack to customize the interface structure.

### Default HTML Template

```html
<div class="cal-root">
  <div class="cal-header">
    <button id="prev-month" class="cal-nav-btn">&#9664;</button>
    <div class="cal-title-wrap">
      <div class="cal-era">{{era_name}}</div>
      <div class="cal-title">{{year}}{{year_label}} {{month_name}}</div>
      <div class="cal-subtitle">{{month_events}} events &middot; {{today_weekday}}</div>
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

### Template Variable Placeholders

HTML uses `{{variable}}` format placeholders, automatically replaced with actual values at runtime by Java. **Custom templates must preserve all placeholders**, otherwise the corresponding position will be blank.

#### Text Variables

| Placeholder | Replaced With | Example Value |
|-------------|---------------|---------------|
| `{{era_name}}` | Era name | Star Era |
| `{{year}}` | Current year number | 3 |
| `{{year_label}}` | "Year" label (localized) | Year |
| `{{month_name}}` | Current month name | Star Month |
| `{{month_events}}` | Current month event count | 5 |
| `{{today_weekday}}` | Today's weekday name | Wednesday |
| `{{today_events_label}}` | "Today's Events" text (localized) | Today's Events |
| `{{client_config_label}}` | "Client Settings" text (localized) | Client Settings |
| `{{calendar_config_label}}` | "Calendar Settings" text (localized) | Calendar Settings |
| `{{add_event_label}}` | "Add Event" text (localized) | Add Event |

#### Dynamic Generation Variables

These variables contain HTML fragments dynamically generated by Java code. **They cannot be manually written**, only introduced via placeholders.

| Placeholder | Generated Content | Structure |
|-------------|-------------------|-----------|
| `{{week_row}}` | Weekday header row | 7 `<div class="cal-week-cell">` |
| `{{day_cells}}` | Date grid | Multiple `<div class="cal-day">` |
| `{{events_content}}` | Today's event list | Multiple `<div class="cal-event-row">` or no-event message |

### Dynamically Generated HTML Structure

#### `{{week_row}}` Generated Content

```html
<div class="cal-week-cell sun">Sunday</div>
<div class="cal-week-cell">Monday</div>
<div class="cal-week-cell">Tuesday</div>
<div class="cal-week-cell">Wednesday</div>
<div class="cal-week-cell">Thursday</div>
<div class="cal-week-cell">Friday</div>
<div class="cal-week-cell sat">Saturday</div>
```

- First cell gets additional `sun` class
- Last cell gets additional `sat` class
- Weekday names are determined by calendar configuration

#### `{{day_cells}}` Generated Content

```html
<!-- Month start padding (empty) -->
<div class="cal-day empty"></div>

<!-- Normal date -->
<div id="day-1" class="cal-day">
  <div class="cal-day-head">
    <div class="cal-day-num">1</div>
  </div>
</div>

<!-- Today -->
<div id="day-15" class="cal-day today">
  <div class="cal-day-head">
    <div class="cal-day-num">15</div>
    <div class="cal-day-dot"></div>
  </div>
  <div class="cal-day-event">Event Name</div>
</div>

<!-- Date with multiple events -->
<div id="day-20" class="cal-day today has-event multi-event">
  <div class="cal-day-head">
    <div class="cal-day-num">20</div>
    <div class="cal-day-dot"></div>
  </div>
  <div class="cal-day-event">First Event</div>
  <div class="cal-day-count">+2</div>
</div>

<!-- Future date -->
<div id="day-25" class="cal-day future">
  <div class="cal-day-head">
    <div class="cal-day-num">25</div>
  </div>
</div>
```

**Day cell class combination rules**:
- Base class: `cal-day`
- Empty padding: append `empty`
- Today: append `today`
- Future date: append `future` (mutually exclusive with `today`)
- Has events: append `has-event`
- 2+ events: append `multi-event`

**Day cell id**: `day-<day_number>`, e.g., `day-1`, `day-15`

#### `{{events_content}}` Generated Content

When no events:
```html
<div class="cal-no-event">✕ No events today</div>
```

When events exist:
```html
<div class="cal-event-row" style="border-left-color:#FFD700">
  <div class="cal-event-icon" style="color:#FFD700">◆</div>
  <div class="cal-event-content">
    <div class="cal-event-name">Event Name</div>
    <div class="cal-event-desc">Event Description</div>
  </div>
  <div class="cal-event-tag">↻</div>
</div>
```

- `border-left-color` and `color` are determined by the player-selected event color
- `cal-event-tag` (↻) only appears when the event is set to "yearly repeat"
- `cal-event-desc` only appears when the event has a description

### Supported HTML Tags

| Tag | Description | Clickable |
|-----|-------------|-----------|
| `<div>` | Generic container | Only with id |
| `<button>` | Button | Yes |
| `<a>` | Link | Yes |
| `<span>` | Inline text container | No |
| `<p>` | Paragraph | No |
| `<h1>` - `<h6>` | Headings | No |
| `<br>` | Line break | No |
| `<hr>` | Horizontal rule | No |
| `<img>` | Image | No |

### Supported HTML Attributes

| Attribute | Description | Example |
|-----------|-------------|---------|
| `class` | CSS class names (space-separated) | `class="cal-day today"` |
| `id` | Unique element identifier, for click events | `id="prev-month"` |
| `style` | Inline styles | `style="color: #FF0000"` |

### Predefined Button ids

The following ids have corresponding click event handlers in Java. **If your custom template includes buttons with these ids, clicking them will trigger the corresponding function**:

| id | Function |
|----|----------|
| `prev-month` | Previous month |
| `next-month` | Next month |
| `open-client-config` | Open client config screen |
| `open-calendar-config` | Open calendar config screen |
| `open-events` | Open event management screen |

**Day cell id**: `day-<number>` (e.g., `day-1`), clicking a day cell selects that date to view events.

### HTML Entity Support

The following HTML entities can be used in templates:

| Type | Format | Examples |
|------|--------|----------|
| Named entities | `&name;` | `&amp;` `&lt;` `&gt;` `&middot;` `&times;` |
| Decimal entities | `&#NNN;` | `&#9664;` (◀) `&#9654;` (▶) `&#128197;` (📅) |
| Hex entities | `&#xNN;` | `&#x25C6;` (◆) `&#x21BB;` (↻) |

### Custom HTML Template Notes

1. **Must preserve all `{{variable}}` placeholders**, otherwise interface content won't be filled
2. **Must preserve predefined button ids** (e.g., `prev-month`, `next-month`), otherwise navigation breaks
3. **Class names must match CSS**, otherwise styles won't apply
4. You can add new `<div>`, `<span>` elements and new classes, but must define corresponding CSS styles
5. `<script>`, `<input>`, `<form>` and other interactive tags are not supported
6. `onclick` and other event attributes are not supported; click events are handled by id in Java
7. Text in templates is automatically escaped to prevent XSS

### Custom HTML Example

Add a decorative subtitle in the header:

```html
<div class="cal-root">
  <div class="cal-header">
    <button id="prev-month" class="cal-nav-btn">&#9664;</button>
    <div class="cal-title-wrap">
      <div class="cal-era">{{era_name}}</div>
      <div class="cal-title">{{year}}{{year_label}} {{month_name}}</div>
      <div class="cal-subtitle">{{month_events}} events &middot; {{today_weekday}}</div>
      <div class="cal-custom-badge">🌙 Custom Text</div>
    </div>
    <button id="next-month" class="cal-nav-btn">&#9654;</button>
  </div>
  <!-- ... rest unchanged ... -->
</div>
```

Then add to CSS:
```css
.cal-custom-badge {
    font-size: __ERA_FONT__px;
    color: #FF6B6B;
    text-align: center;
}
```

---

## Complete Example

### Example 1: Neon Style

`styles.json`:
```json
{
  "styles": [
    {
      "id": "neon",
      "name": "Neon",
      "description": "Cyberpunk neon style",
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

### Example 2: Override Built-in Style

To modify the built-in "Dark" style, use the same id in your resource pack:

```json
{
  "styles": [
    {
      "id": "dark",
      "name": "Dark (Modified)",
      "description": "My modified dark style",
      "file": "styles/my_dark.css",
      "builtin": false
    }
  ]
}
```

Your `my_dark.css` will replace the mod's built-in `dark.css`. When players select "Dark", they'll see your style.

---

## FAQ

### Q: Can I skip dynamic variables?

**No.** Variables like `__DAY_H__px` and `__ROOT_PAD__px` are dynamically calculated by Java based on screen resolution. Using fixed values (e.g., `45px`) will cause layout issues on different resolutions. **All size-related properties must use variables.**

### Q: Can I use only some variables?

Yes, but not recommended. Unused variables won't cause errors, but the corresponding elements will use default values (possibly 0), causing size issues.

### Q: Can I add new CSS classes?

You can add classes, but the HTML template is fixed. New classes won't automatically appear in the HTML unless you also override `calendar_screen.html` via resource pack.

### Q: Can I override the HTML template?

Yes. Place `assets/calendarmod/templates/calendar_screen.html` in your resource pack. See [HTML Template Reference](#html-template-reference) section for details.

### Q: Why isn't my style showing up?

Check the following:
1. `pack.mcmeta` has `pack_format: 15`
2. The resource pack is enabled in-game
3. The `file` path in `styles.json` is correct
4. The CSS file exists at the specified path
5. `styles.json` is valid JSON
6. `id` is not empty

### Q: Can I remove built-in styles?

No. The resource pack's `styles.json` is merged with the mod's built-in one. You can only override (same id) or add new styles, not remove built-in ones.

### Q: What happens if multiple resource packs add styles?

All resource packs' `styles.json` files are merged. Same-id styles: higher-priority pack overrides lower. Different-id styles: all are kept.

### Q: What happens if I don't write the hud field?

The HUD will use the default gray-white translucent colors and won't affect the main calendar interface (which is controlled by the CSS file). Only by writing the `hud` field will the HUD switch to match your style.

### Q: When overriding a built-in style, are hud fields merged field-by-field?

**No.** If you override a built-in style (same `id`) and write a `hud` field, your `hud` config **completely replaces** the mod's built-in HUD colors. If you only write some sub-fields (e.g., only `body` and `textPrimary`), the missing fields fall back to **default gray-white values**, and do **not** inherit the corresponding fields from the overridden style. We recommend writing all 7 fields when overriding.

### Q: Why aren't my HUD colors taking effect?

Check the following:
1. The `hud` object in `styles.json` is spelled correctly (lowercase)
2. The color string is a valid hex format (`#RRGGBB`, `#RRGGBBAA`, etc.)
3. The alpha value isn't too low (e.g., `#00000000` is fully transparent and invisible)
4. The currently selected style is the one you modified (confirm in client settings)

---

## License

LGPL-2.1
