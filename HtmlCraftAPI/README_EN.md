# HtmlCraft API

> HTML/CSS GUI Rendering Engine for Minecraft 1.20.1 Forge / Fabric

## Introduction

**HtmlCraft API** is a lightweight HTML/CSS rendering engine that brings web frontend technologies into Minecraft GUI development. Instead of learning the complex Minecraft GUI API (`GuiGraphics`, `Widget`, `AbstractContainerScreen`, etc.), developers can build beautiful game interfaces by simply writing HTML + CSS.

---

## Features

| Feature | Description |
|---------|-------------|
| HTML Parsing | Common HTML tags, attributes, nested structures, HTML entities (named/decimal/hex) |
| CSS Styling | Class selectors, ID selectors, tag selectors, inline styles |
| Layout Engine | Block layout, Flexbox, CSS Grid |
| Visual Effects | Linear gradients, border radius, shadow simulation, opacity |
| Interaction | Button click events, mouse wheel scrolling, hit testing |
| Template Engine | `{{variable}}` replacement, data binding context |
| Thread Safety | ConcurrentHashMap, CopyOnWriteArrayList |

---

## Requirements

This API ships in two versions, Forge and Fabric. Pick the jar matching your loader.

### Forge Version

| Dependency | Version |
|------------|---------|
| Minecraft | 1.20.1 |
| Forge | 47.x |
| Java | 17 |

### Fabric Version

| Dependency | Version |
|------------|---------|
| Minecraft | 1.20.1 |
| Fabric Loader | >=0.19.3 |
| Fabric API | 0.92.11+1.20.1 |
| Java | 17 |

---

## Installation

### As a Mod Dependency

- Forge: place `htmlcraftapi-1.0.0-1.20.1forge.jar` into the `mods` folder
- Fabric: place `htmlcraftapi-1.0.0-1.20.1fabric.jar` into the `mods` folder

### As a Development Dependency

**Forge version** (Mojang mappings):

```gradle
dependencies {
    implementation files('libs/htmlcraftapi-1.0.0-1.20.1forge.jar')
}
```

**Fabric version** (Yarn mappings, with Loom composite build):

```gradle
dependencies {
    modImplementation 'com.htmlcraft.api:htmlcraftapi:1.0.0-1.20.1fabric'
}
```

For Fabric, `includeBuild` is recommended to bring in the source project for debugging:

```gradle
// settings.gradle
includeBuild '../HtmlCraftAPI/fabric'
```

---

## Quick Start

> Examples below use Forge (Mojang mappings) API. The Fabric version only differs in class names (e.g. `Component` → `Text`, `Minecraft` → `MinecraftClient`, `GuiGraphics` → `DrawContext`); the HTML/CSS API is identical.

### 1. Create a Screen via Builder

```java
import com.htmlcraft.api.HtmlRendererAPI;
import com.htmlcraft.api.binding.DataContext;
import com.htmlcraft.api.screen.HtmlScreen;
import net.minecraft.network.chat.Component;

DataContext data = new DataContext();
data.set("title", "My Interface");
data.set("content", "Hello, Minecraft!");

HtmlScreen screen = HtmlRendererAPI.createScreen(Component.literal("My Screen"))
    .html("<div class='container'>" +
          "  <h1>{{title}}</h1>" +
          "  <p>{{content}}</p>" +
          "  <button id='btn-ok'>OK</button>" +
          "</div>")
    .css(".container { padding: 20px; background: #1a1a2e; border-radius: 10px; }" +
         "h1 { color: #e94560; font-size: 16px; }" +
         "p { color: #ffffff; }" +
         "#btn-ok { background: #0f3460; color: #ffffff; border-radius: 6px; padding: 8px; }")
    .data(data)
    .onClick(event -> {
        if ("btn-ok".equals(event.element().getId())) {
            System.out.println("Player clicked the OK button");
        }
    })
    .build();

Minecraft.getInstance().setScreen(screen);
```

### 2. Extend HtmlScreen for Custom Screens

```java
public class MyScreen extends HtmlScreen {

    public MyScreen() {
        super(Component.literal("My Screen"));
    }

    @Override
    protected String getHtml() {
        return "<div class='root'>Custom content</div>";
    }

    @Override
    protected String getCss() {
        return ".root { padding: 16px; background: #2d2d44; }";
    }
}
```

### 3. Forge / Fabric Mapping Reference

| Forge (Mojang) | Fabric (Yarn) | Description |
|-----------------|---------------|-------------|
| `net.minecraft.network.chat.Component` | `net.minecraft.text.Text` | Text component |
| `net.minecraft.client.Minecraft` | `net.minecraft.client.MinecraftClient` | Client instance |
| `net.minecraft.client.gui.GuiGraphics` | `net.minecraft.client.gui.DrawContext` | Draw context |
| `net.minecraft.world.level.saveddata.SavedData` | `net.minecraft.world.PersistentState` | Save persistence |
| `net.minecraft.network.FriendlyByteBuf` | `net.minecraft.network.PacketByteBuf` | Network buffer |
| `net.minecraftforge.network.SimpleChannel` | `ServerPlayNetworking` / `ClientPlayNetworking` | Network channel |

---

## Core API

### HtmlRendererAPI

Unified entry point providing a Builder pattern for creating HTML Screens.

| Method | Description |
|--------|-------------|
| `createScreen(Component title)` | Create a Screen builder |

### HtmlRendererAPI.Builder

Chained builder for configuring HTML, CSS, data, and events.

| Method | Description |
|--------|-------------|
| `html(String html)` | Set HTML content |
| `css(String css)` | Set CSS stylesheet |
| `data(DataContext context)` | Set template data context |
| `onClick(Consumer<ClickEvent> handler)` | Set click event callback |
| `build()` | Build the HtmlScreen instance |

### HtmlScreen

Abstract HTML rendering screen base class, extends Minecraft `Screen`.

| Method | Description |
|--------|-------------|
| `getHtml()` | Abstract method, subclass provides HTML content |
| `getCss()` | Override to provide CSS styles |
| `setClickHandler(Consumer<ClickEvent>)` | Set click event handler |
| `setPreferredSize(int w, int h)` | Set content area size (centered layout) |
| `rebuild()` | Rebuild render pipeline (refresh after data changes) |
| `getRootLayout()` | Get layout root node |
| `getPipeline()` | Get render pipeline |

### HtmlScreen.ClickEvent

Click event containing the clicked element and coordinates.

| Method | Description |
|--------|-------------|
| `element()` | The clicked DOM element |
| `button()` | Mouse button (0=left, 1=right, 2=middle) |
| `x()` / `y()` | Click coordinates |

### DataContext

Template variable context, thread-safe.

| Method | Description |
|--------|-------------|
| `set(String key, Object value)` | Set a variable |
| `get(String key)` | Get a variable |
| `getString(String key)` | Get string value |
| `has(String key)` | Check if variable exists |
| `clear()` | Clear all variables |

### HtmlElement

DOM element node.

| Method | Description |
|--------|-------------|
| `tagName()` | Get tag name |
| `getId()` | Get id attribute |
| `hasClass(String)` | Check if has class |
| `getAttribute(String)` | Get attribute value |
| `children()` | Get child nodes |

---

## Supported CSS Properties

### Layout

| Property | Values |
|----------|--------|
| `display` | `block` / `flex` / `inline` / `grid` / `none` |
| `flex-direction` | `row` / `column` |
| `justify-content` | `flex-start` / `center` / `flex-end` / `space-between` / `space-around` |
| `align-items` | `flex-start` / `center` / `flex-end` / `stretch` |
| `gap` | Pixel value |
| `grid-template-columns` | `repeat(N, 1fr)` |

### Box Model

| Property | Description |
|----------|-------------|
| `width` / `height` | Pixel value, `auto` |
| `padding` | Pixel value |
| `margin` | Pixel value |
| `border-width` | Pixel value |

### Visual Effects

| Property | Values |
|----------|--------|
| `background` | `#RRGGBB` / `#AARRGGBB` / `#RRGGBBAA` |
| `background` (gradient) | `linear-gradient(to bottom, #color1, #color2)` |
| `color` | Text color |
| `border-color` | Border color |
| `border-radius` | Corner radius in pixels |
| `box-shadow` | `offsetX offsetY blur spread #color` |
| `opacity` | `0.0` - `1.0` |

### Text

| Property | Values |
|----------|--------|
| `font-size` | Pixel value |
| `text-align` | `left` / `center` / `right` |

### Position & Overflow

| Property | Values |
|----------|--------|
| `position` | `static` / `relative` / `absolute` / `fixed` |
| `overflow-x` / `overflow-y` | `visible` / `hidden` / `scroll` / `auto` |
| `z-index` | Integer |

---

## Template Engine

Use `{{variable}}` placeholders in HTML. They are replaced by `TemplateEngine.render()` with values from `DataContext` during `init()`.

```java
DataContext data = new DataContext();
data.set("player_name", "Steve");
data.set("level", 30);

// HTML template
String html = "<div>Player: {{player_name}} | Level: {{level}}</div>";
```

Special characters are automatically escaped to HTML entities to prevent injection.

---

## Render Pipeline

```
HTML String
    ↓
HtmlParser (parse to DOM tree)
    ↓
StyleSheet + CssParser (load CSS rules)
    ↓
StyleCalculator (compute final styles per element)
    ↓
LayoutEngine (perform layout, generate LayoutNode tree)
    ↓
RenderPipeline (generate and execute render commands)
    ↓
GuiGraphics (draw to Minecraft screen)
```

---

## Package Structure

```
com.htmlcraft.api
├── HtmlCraftAPI.java          // Mod main class
├── HtmlRendererAPI.java       // Public entry point + Builder
├── core/
│   ├── HtmlDocument.java      // HTML document
│   ├── HtmlElement.java       // DOM element node
│   ├── HtmlNode.java          // DOM node interface
│   └── HtmlText.java          // DOM text node
├── parser/
│   ├── HtmlParser.java        // HTML parser
│   └── CssParser.java         // CSS parser
├── style/
│   ├── StyleSheet.java        // CSS stylesheet
│   ├── StyleCalculator.java   // Style calculator
│   └── ComputedStyle.java     // Computed style
├── layout/
│   ├── LayoutEngine.java      // Layout engine
│   └── LayoutNode.java        // Layout node
├── render/
│   ├── RenderPipeline.java    // Render pipeline
│   ├── RenderContext.java     // Render context
│   ├── RenderCommand.java     // Render command interface
│   └── commands/              // Concrete render commands
│       ├── FillRectCommand.java
│       ├── DrawTextCommand.java
│       ├── DrawBorderCommand.java
│       └── BlitTextureCommand.java
├── binding/
│   ├── DataContext.java       // Data binding context
│   └── TemplateEngine.java    // Template engine
└── screen/
    └── HtmlScreen.java        // HTML rendering screen base class
```

---

## Click Events

Clickable elements include:
- `<button>` tags
- `<a>` tags
- Any element with an `id` attribute

Clicks trigger a `ClickEvent` callback. Use `event.element().getId()` to identify which element was clicked.

---

## Scroll Support

Elements with `overflow-y: scroll` or `overflow-y: auto` automatically respond to mouse wheel scrolling. Each scroll step moves 20 pixels.

---

## License

LGPL-2.1

## Author

Yifei
