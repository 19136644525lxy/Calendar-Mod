#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Calendar Mod 资源包生成器
==========================
为 Calendar Mod 快速生成自定义样式资源包。

功能：
  1. 可视化编辑 styles.json 配置
  2. 实时预览 CSS 样式效果
  3. HUD 颜色配置与日历界面同步
  4. 内置样式参考，一键套用模板
  5. 资源包压缩导出
  6. 智能颜色格式校验与转换

运行方式：
  python resource_pack_generator.py

依赖：
  pip install -r requirements.txt
"""

import base64
import json
import os
import re
import shutil
import sys
import zipfile
from datetime import datetime
from pathlib import Path
from typing import Optional

try:
    from PySide6.QtCore import Qt, Signal, QSize
    from PySide6.QtGui import QColor, QIcon, QFont, QFontDatabase, QAction, QPixmap, QPainter
    from PySide6.QtWidgets import (
        QApplication, QMainWindow, QWidget, QDialog, QDialogButtonBox,
        QVBoxLayout, QHBoxLayout, QFormLayout, QGridLayout,
        QTabWidget, QTabBar,
        QLineEdit, QTextEdit, QPlainTextEdit, QSpinBox, QDoubleSpinBox,
        QCheckBox, QComboBox, QListWidget, QListWidgetItem,
        QPushButton, QLabel, QGroupBox, QFrame,
        QFileDialog, QColorDialog, QMessageBox, QInputDialog,
        QSplitter, QTreeWidget, QTreeWidgetItem,
        QProgressBar, QStatusBar, QToolBar,
        QScrollArea, QSizePolicy, QStyle,
    )
except ImportError:
    print("错误：缺少 PySide6 依赖。")
    print("请运行：pip install PySide6>=6.6.0")
    sys.exit(1)

try:
    from rich.console import Console
    console = Console()
    RICH_AVAILABLE = True
except ImportError:
    RICH_AVAILABLE = False
    console = None


# ============================================================
# 颜色工具
# ============================================================

HEX_RE = re.compile(r'^#([0-9A-Fa-f]{3,8})$')


def parse_hex_color(s: str) -> Optional[str]:
    """解析十六进制颜色，支持 #RGB #RGBA #RRGGBB #RRGGBBAA，统一输出 #RRGGBBAA"""
    s = s.strip()
    if not s.startswith('#'):
        return None
    m = HEX_RE.match(s)
    if not m:
        return None
    raw = m.group(1)
    if len(raw) == 3:
        r, g, b = raw[0] * 2, raw[1] * 2, raw[2] * 2
        return f"#{r}{g}{b}FF"
    elif len(raw) == 4:
        r, g, b, a = raw[0] * 2, raw[1] * 2, raw[2] * 2, raw[3] * 2
        return f"#{r}{g}{b}{a}"
    elif len(raw) == 6:
        return f"#{raw}FF"
    elif len(raw) == 8:
        return f"#{raw}"
    return None


def hex_to_qcolor(hex_str: str) -> QColor:
    """将 #RRGGBBAA 转为 QColor"""
    norm = parse_hex_color(hex_str)
    if not norm:
        return QColor("#000000")
    r = int(norm[1:3], 16)
    g = int(norm[3:5], 16)
    b = int(norm[5:7], 16)
    a = int(norm[7:9], 16)
    return QColor(r, g, b, a)


def qcolor_to_hex(color: QColor) -> str:
    """QColor 转为 #RRGGBBAA"""
    return f"#{color.red():02X}{color.green():02X}{color.blue():02X}{color.alpha():02X}"


# ============================================================
# 中文对话框封装（解决 QMessageBox / QInputDialog 默认英文按钮 + 黑底问题）
#   - 用户截图里 Yes / No / OK / Cancel 全是英文，且黑底时看不清，
#     很容易点错（比如"恢复初始"时点了 No 以为没生效）。
#   - 显式 setStyleSheet 给对话框本身指定纯色背景，避免 transparent 继承导致变黑。
# ============================================================

def dlg_confirm(parent, title: str, message: str,
                yes_text: str = "是(&Y)", no_text: str = "否(&N)",
                default_no: bool = True) -> bool:
    """中文确认框（替代 QMessageBox.question）。返回 True=是, False=否。"""
    dlg = QMessageBox(QMessageBox.Question, title, message,
                      QMessageBox.NoButton, parent)
    dlg.addButton(yes_text, QMessageBox.YesRole)
    dlg.addButton(no_text, QMessageBox.NoRole)
    if default_no:
        dlg.setDefaultButton(dlg.buttons()[-1])
    else:
        dlg.setDefaultButton(dlg.buttons()[0])
    # 显式指定对话框与内部控件的安全纯色背景，避免透明继承变黑
    safe_bg = "#FFFFFF" if parent is None else None
    _apply_dialog_safe_style(dlg, safe_bg_palette=parent)
    dlg.exec()
    return dlg.buttonRole(dlg.clickedButton()) == QMessageBox.YesRole


def dlg_info(parent, title: str, message: str):
    """中文信息框（替代 QMessageBox.information）。"""
    dlg = QMessageBox(QMessageBox.Information, title, message,
                      QMessageBox.NoButton, parent)
    dlg.addButton("确定", QMessageBox.AcceptRole)
    _apply_dialog_safe_style(dlg, safe_bg_palette=parent)
    dlg.exec()


def dlg_warn(parent, title: str, message: str):
    """中文警告框（替代 QMessageBox.warning）。"""
    dlg = QMessageBox(QMessageBox.Warning, title, message,
                      QMessageBox.NoButton, parent)
    dlg.addButton("确定", QMessageBox.AcceptRole)
    _apply_dialog_safe_style(dlg, safe_bg_palette=parent)
    dlg.exec()


def dlg_error(parent, title: str, message: str):
    """中文错误框（替代 QMessageBox.critical）。"""
    dlg = QMessageBox(QMessageBox.Critical, title, message,
                      QMessageBox.NoButton, parent)
    dlg.addButton("确定", QMessageBox.AcceptRole)
    _apply_dialog_safe_style(dlg, safe_bg_palette=parent)
    dlg.exec()


def dlg_ask_item(parent, title: str, label: str, items: list,
                 current: int = 0, editable: bool = False):
    """中文下拉选择框（替代 QInputDialog.getItem）。返回 (str, True) 或 ("", False)。"""
    from PySide6.QtWidgets import QDialog, QDialogButtonBox, QComboBox, QLabel, QVBoxLayout
    dlg = QDialog(parent)
    dlg.setWindowTitle(title)
    dlg.setMinimumWidth(360)
    v = QVBoxLayout(dlg)
    v.setContentsMargins(16, 16, 16, 16)
    v.setSpacing(12)
    v.addWidget(QLabel(label))
    combo = QComboBox()
    combo.addItems(items)
    combo.setCurrentIndex(max(0, min(current, len(items) - 1)))
    if editable:
        combo.setEditable(True)
    v.addWidget(combo)
    bb = QDialogButtonBox()
    ok_btn = bb.addButton("确定", QDialogButtonBox.AcceptRole)
    cancel_btn = bb.addButton("取消", QDialogButtonBox.RejectRole)
    bb.accepted.connect(dlg.accept)
    bb.rejected.connect(dlg.reject)
    v.addWidget(bb)
    _apply_dialog_safe_style(dlg, safe_bg_palette=parent)
    if dlg.exec() == QDialog.Accepted:
        return combo.currentText(), True
    return "", False


def dlg_ask_text(parent, title: str, label: str, default: str = ""):
    """中文文本输入框（替代 QInputDialog.getText）。"""
    from PySide6.QtWidgets import QDialog, QDialogButtonBox, QLineEdit, QLabel, QVBoxLayout
    dlg = QDialog(parent)
    dlg.setWindowTitle(title)
    dlg.setMinimumWidth(360)
    v = QVBoxLayout(dlg)
    v.setContentsMargins(16, 16, 16, 16)
    v.setSpacing(12)
    v.addWidget(QLabel(label))
    edit = QLineEdit(default)
    v.addWidget(edit)
    bb = QDialogButtonBox()
    bb.addButton("确定", QDialogButtonBox.AcceptRole)
    bb.addButton("取消", QDialogButtonBox.RejectRole)
    bb.accepted.connect(dlg.accept)
    bb.rejected.connect(dlg.reject)
    v.addWidget(bb)
    _apply_dialog_safe_style(dlg, safe_bg_palette=parent)
    if dlg.exec() == QDialog.Accepted:
        return edit.text(), True
    return "", False


def _apply_dialog_safe_style(dlg, safe_bg_palette=None):
    """给顶层弹出对话框强制设置纯色安全背景 + 中文按钮文字可读色。

    黑底根因：子对话框（QMessageBox / QInputDialog / StyleEditDialog）如果
    继承了 `QDialog > QWidget { background: transparent; }`，在 Windows 下
    没有父容器像素可合成时会 fallback 为纯黑。
    解决：对所有对话框一律 setStyleSheet 显式设置 background: #FFFFFF/#1E293B。
    """
    # 判断当前亮/暗
    is_dark = False
    if safe_bg_palette is not None and hasattr(safe_bg_palette, '_dark_theme'):
        is_dark = bool(getattr(safe_bg_palette, '_dark_theme'))
    bg_panel = "#1E293B" if is_dark else "#FFFFFF"
    fg = "#E2E8F0" if is_dark else "#1E293B"
    btn_bg = "#F1F5F9" if not is_dark else "#334155"
    btn_border = "#CBD5E1" if not is_dark else "#475569"
    btn_fg = fg
    dlg.setStyleSheet(f"""
        QDialog, QMessageBox, QInputDialog {{
            background: {bg_panel} !important;
        }}
        QLabel {{
            color: {fg};
            background: transparent;
        }}
        QPushButton {{
            background: {btn_bg};
            color: {btn_fg};
            border: 1px solid {btn_border};
            border-radius: 6px;
            padding: 6px 16px;
            min-width: 70px;
        }}
        QPushButton:hover {{
            background: {'#E2E8F0' if not is_dark else '#475569'};
        }}
        QComboBox, QLineEdit {{
            background: {'#FFFFFF' if not is_dark else '#0F172A'};
            color: {fg};
            border: 1px solid {btn_border};
            border-radius: 6px;
            padding: 5px 10px;
            selection-background-color: #6366F1;
            selection-color: #FFFFFF;
        }}
    """)


# ============================================================
# 数据模型
# ============================================================

HUD_FIELDS = [
    ("shadow", "阴影颜色"),
    ("body", "主体背景"),
    ("decor", "装饰条颜色"),
    ("border", "边框颜色"),
    ("textPrimary", "主文字"),
    ("textSecondary", "次要文字"),
    ("textEvent", "事件文字"),
]

DEFAULT_HUD = {
    "shadow": "#00000028",
    "body": "#F7F7F8E8",
    "decor": "#E4E7ECFF",
    "border": "#0000001A",
    "textPrimary": "#1E293BFF",
    "textSecondary": "#475569FF",
    "textEvent": "#B45309FF",
}

BUILTIN_STYLES = {
    "default": {
        "name": "灰白(默认)",
        "description": "默认灰白色调",
        "file": "calendar_screen.css",
        "hud": {"shadow": "#00000028", "body": "#F7F7F8E8", "decor": "#E4E7ECFF",
                "border": "#0000001A", "textPrimary": "#1E293BFF",
                "textSecondary": "#475569FF", "textEvent": "#B45309FF"},
    },
    "dark": {
        "name": "暗夜",
        "description": "深色暗色调",
        "file": "styles/dark.css",
        "hud": {"shadow": "#00000060", "body": "#1E1E24E8", "decor": "#2A2A32FF",
                "border": "#FFFFFF1A", "textPrimary": "#FFFFFFFF",
                "textSecondary": "#A0A0B0FF", "textEvent": "#FFB74DFF"},
    },
    "ocean": {
        "name": "海洋",
        "description": "蓝色科技风",
        "file": "styles/ocean.css",
        "hud": {"shadow": "#1976D230", "body": "#E3F2FDE8", "decor": "#1976D2FF",
                "border": "#1976D220", "textPrimary": "#0D47A1FF",
                "textSecondary": "#546E7AFF", "textEvent": "#E65100FF"},
    },
    "forest": {
        "name": "森林",
        "description": "绿色自然风",
        "file": "styles/forest.css",
        "hud": {"shadow": "#2E7D3228", "body": "#E8F5E9E8", "decor": "#2E7D32FF",
                "border": "#2E7D3218", "textPrimary": "#1B5E20FF",
                "textSecondary": "#388E3CFF", "textEvent": "#E65100FF"},
    },
    "mystic": {
        "name": "幻境",
        "description": "紫色神秘风",
        "file": "styles/mystic.css",
        "hud": {"shadow": "#6A1B9A28", "body": "#F3E5F5E8", "decor": "#6A1B9AFF",
                "border": "#6A1B9A18", "textPrimary": "#4A148CFF",
                "textSecondary": "#7B1FA2FF", "textEvent": "#E65100FF"},
    },
    "minimal": {
        "name": "极简",
        "description": "简约扁平风",
        "file": "styles/minimal.css",
        "hud": {"shadow": "#00000014", "body": "#FAFAFAE8", "decor": "#EEEEEEFF",
                "border": "#0000000A", "textPrimary": "#212121FF",
                "textSecondary": "#757575FF", "textEvent": "#E65100FF"},
    },
}


# ============================================================
# 内置 CSS 模板：6 个样式各一份（从 BUILTIN_STYLES[style_id]["hud"] 取配色）
#  - 让用户选中任何一个内置样式，CSS 编辑器立刻显示对应风格
#  - 导出时每个 entry 自己的 CSS 写到自己的 file 路径
# ============================================================

def _drop_alpha(hex8: str) -> str:
    """把 #RRGGBBAA 截成 #RRGGBB（CSS 里不需要 Alpha，用 rgba 另写透明度时方便）"""
    if hex8 and len(hex8) >= 7:
        return hex8[:7]
    return hex8 or "#FFFFFF"


def _to_rgba(hex8: str, alpha_override: float | None = None) -> str:
    """把 #RRGGBBAA 转成 rgba(r,g,b,a) 字符串。支持覆写 alpha（0.0-1.0）。"""
    if not hex8:
        return "rgba(255,255,255,1)"
    h = hex8.lstrip("#")
    if len(h) == 6:
        h += "FF"
    r, g, b, a = (int(h[i:i + 2], 16) for i in (0, 2, 4, 6))
    a_float = alpha_override if alpha_override is not None else (a / 255.0)
    return f"rgba({r},{g},{b},{a_float:.3f})"


def _base_css_template(style_name: str, hud: dict, *, minimal: bool = False) -> str:
    """生成一份带季节风格的日历 CSS。结构：头部 / 导航 / 日期格子 / 今天高亮 / 事件点 / 事件卡片。"""
    body       = _drop_alpha(hud.get("body", "#F7F7F8E8"))
    decor      = _drop_alpha(hud.get("decor", "#E4E7ECFF"))
    primary    = _drop_alpha(hud.get("textPrimary", "#1E293BFF"))
    secondary  = _drop_alpha(hud.get("textSecondary", "#475569FF"))
    event_txt  = _drop_alpha(hud.get("textEvent", "#B45309FF"))
    border     = _to_rgba(hud.get("border", "#0000001A"))
    body_rgba  = _to_rgba(hud.get("body", "#F7F7F8E8"))
    decor_rgba = _to_rgba(hud.get("decor", "#E4E7ECFF"))
    shadow     = _to_rgba(hud.get("shadow", "#00000028"))
    # 今天高亮：取 decor 主色再叠加 20% 透明度
    today_bg   = _to_rgba(hud.get("decor", "#E4E7ECFF"), 0.20)
    # 事件卡片背景：取 body 与 decor 的融合（装饰色 18% 透明）
    card_bg    = _to_rgba(hud.get("decor", "#E4E7ECFF"), 0.18)

    if minimal:
        shadow_css      = "box-shadow: none;"
        radius_lg       = "2px"
        radius_sm       = "2px"
        header_padding  = "12px"
        event_padding   = "8px 10px"
        card_border     = f"1px solid {border}"
    else:
        shadow_css      = f"box-shadow: 0 6px 18px {shadow};"
        radius_lg       = "12px"
        radius_sm       = "8px"
        header_padding  = "16px 20px"
        event_padding   = "12px 14px"
        card_border     = "none"

    return f"""/* ================================================================
   Calendar Style: {style_name}
   - 由 Calendar Mod 资源包生成器自动生成
   - 与 styles.json 中此样式的 hud 颜色配置一致
   ================================================================ */

.cal-root {{
    background: {body};
    background-image: linear-gradient(160deg, {body_rgba}, {decor_rgba});
    color: {primary};
    font-family: system-ui, "PingFang SC", "Microsoft YaHei", sans-serif;
    border-radius: {radius_lg};
    {shadow_css}
    overflow: hidden;
}}

/* ===== 头部：标题 + 月份导航 ===== */
.cal-header {{
    padding: {header_padding};
    border-bottom: {card_border if minimal else f"1px solid {border}"};
    display: flex;
    align-items: center;
    justify-content: space-between;
    background: linear-gradient(90deg, {decor_rgba}, transparent);
}}

.cal-title {{
    font-size: 18px;
    font-weight: 700;
    color: {primary};
    letter-spacing: 0.3px;
}}

.cal-subtitle {{
    color: {secondary};
    font-size: 12px;
    margin-top: 4px;
}}

/* ===== 月份导航按钮 ===== */
.cal-nav-btn {{
    background: {body};
    border: 1px solid {border};
    border-radius: {radius_sm};
    color: {primary};
    padding: 6px 14px;
    font-size: 13px;
    transition: background .15s ease, border-color .15s ease, transform .1s ease;
    cursor: pointer;
}}

.cal-nav-btn:hover {{
    background: {decor};
    border-color: {primary};
    color: {primary};
}}

.cal-nav-btn:active {{
    transform: translateY(1px);
}}

/* ===== 星期表头：日一二三四五六 ===== */
.cal-weekdays {{
    display: flex;
    padding: 8px 10px 2px;
    color: {secondary};
    font-size: 12px;
    text-transform: uppercase;
    letter-spacing: 1px;
}}

.cal-weekday {{
    flex: 1 1 0;
    text-align: center;
    padding: 4px 0;
    font-weight: 600;
}}

/* ===== 日期网格 ===== */
.cal-days {{
    display: flex;
    flex-wrap: wrap;
    padding: 4px 10px 14px;
    gap: 2px 0;
}}

.cal-day {{
    display: block;
    width: calc(100% / 7);
    box-sizing: border-box;
    text-align: center;
    padding: 10px 0;
    color: {primary};
    font-size: 13px;
    border-radius: {radius_sm};
    position: relative;
    transition: background .12s ease, color .12s ease;
    cursor: default;
}}

.cal-day:hover {{
    background: {card_bg};
}}

/* 今天高亮：用 decor 主色 + 半透明，让风格色调立刻显现 */
.cal-day.today {{
    background: {today_bg};
    color: {primary};
    font-weight: 700;
    border: 1px solid {decor};
    {"" if minimal else f"box-shadow: inset 0 0 0 1px {decor};"}
}}

/* 其他月份的日期：淡色显示 */
.cal-day.other-month {{
    color: {secondary};
    opacity: 0.55;
}}

/* 有事件的日期：在格子下方点一个装饰色圆点 */
.cal-day.has-event::after {{
    content: "";
    display: block;
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: {event_txt};
    margin: 4px auto 0;
}}

/* ===== 事件列表卡片 ===== */
.cal-events {{
    padding: 0 16px 20px;
}}

.cal-event-title {{
    font-size: 12px;
    color: {secondary};
    font-weight: 600;
    margin: 0 4px 10px;
    text-transform: uppercase;
    letter-spacing: 1px;
}}

.cal-event {{
    padding: {event_padding};
    background: {card_bg};
    border: {card_border};
    border-radius: {radius_sm};
    margin-bottom: 8px;
    color: {primary};
    font-size: 13px;
    line-height: 1.5;
}}

.cal-event .cal-event-time {{
    color: {event_txt};
    font-weight: 600;
    font-size: 12px;
    margin-right: 8px;
}}

/* 周末：用 secondary 稍微强调一下（可选视觉，不破坏整体） */
.cal-day.weekend {{
    color: {secondary};
}}
"""


def builtin_css_for(style_id: str) -> str:
    """根据样式 id，从 BUILTIN_STYLES 取 hud 配色，生成对应风格的 CSS 模板。"""
    if style_id not in BUILTIN_STYLES:
        return _base_css_template("Unknown", {}, minimal=False)
    style_data = BUILTIN_STYLES[style_id]
    minimal = (style_id == "minimal")
    return _base_css_template(style_data["name"], style_data["hud"], minimal=minimal)


# 3 套用户可一键「套用」的全局模板（独立于 6 个内置样式，用户可以选任意风格覆盖当前选中样式）
def template_simple_white() -> str:
    return _base_css_template("简约白底",
                              {"shadow": "#00000014", "body": "#FFFFFFFF",
                               "decor": "#F1F5F9FF", "border": "#0000001A",
                               "textPrimary": "#1E293BFF", "textSecondary": "#64748BFF",
                               "textEvent": "#DC2626FF"},
                              minimal=False)


def template_dark_night() -> str:
    return _base_css_template("暗色深夜",
                              {"shadow": "#00000080", "body": "#0F172AE8",
                               "decor": "#1E293BFF", "border": "#FFFFFF1A",
                               "textPrimary": "#F1F5F9FF", "textSecondary": "#94A3B8FF",
                               "textEvent": "#FBBF24FF"},
                              minimal=False)


def template_vibrant_gradient() -> str:
    return _base_css_template("活力渐变",
                              {"shadow": "#6366F130", "body": "#EEF2FFE8",
                               "decor": "#6366F1FF", "border": "#6366F120",
                               "textPrimary": "#312E81FF", "textSecondary": "#4338CAFF",
                               "textEvent": "#DB2777FF"},
                              minimal=False)


class StyleEntry:
    """单个样式条目

    每个样式独立保存自己的 CSS 内容（不再是全局单例编辑器共享一份）。
    - id/name/description/file/builtin/hud：元数据（file 是导出时的相对路径）
    - css：该样式的完整 CSS 模板字符串（用户直接在 CSS 编辑器里编辑的内容）
    """

    def __init__(self, id_="", name="", description="", file="",
                 builtin=False, hud=None, css=""):
        self.id = id_
        self.name = name
        self.description = description
        self.file = file
        self.builtin = builtin
        self.hud = hud or {}
        self.css = css  # 每个样式独立的 CSS 模板（导出时写入 self.file）

    def to_dict(self) -> dict:
        d = {
            "id": self.id,
            "name": self.name or self.id,
            "description": self.description,
            "file": self.file,
            "builtin": self.builtin,
        }
        if self.hud:
            d["hud"] = dict(self.hud)
        # CSS 内容用 base64 编码存储，避免 styles.json 里出现大量 \n 转义乱码
        # （JSON 规范强制要求字符串内的换行用 \n 转义，多行 CSS 会变成一行巨长的 \n 串）
        if self.css:
            d["css_b64"] = self._encode_css(self.css)
        return d

    @classmethod
    def _encode_css(cls, css: str) -> str:
        """CSS 原文 → base64 字符串（单行 ASCII，可直接 JSON 序列化）"""
        # 使用 iso-8859-1 把 Python str 映射到 bytes 的 1:1 版本（base64 要求 bytes 输入）
        raw = css.encode("utf-8")
        return base64.b64encode(raw).decode("ascii")

    @classmethod
    def _decode_css(cls, b64: str) -> str:
        """base64 字符串 → CSS 原文（自动处理 UTF-8 编码）"""
        if not b64:
            return ""
        try:
            return base64.b64decode(b64, validate=True).decode("utf-8")
        except Exception:
            # 兼容旧版：如果 base64 解码失败，可能是老版本直接存了带 \n 转义的 CSS
            # 回退：直接用 text (JSON 加载后已经是原文，\n 变成真正的换行)
            return b64

    @classmethod
    def from_dict(cls, d: dict) -> "StyleEntry":
        css = ""
        # 新版优先：css_b64（base64 编码，干净）
        if "css_b64" in d:
            css = cls._decode_css(d["css_b64"])
        # 兼容旧版：css 字段直接存原文（加载后 \n 已经变成真正的换行符，不会再出现 \n 字面量）
        elif "css" in d:
            css = d.get("css", "")
        return cls(
            id_=d.get("id", ""),
            name=d.get("name", ""),
            description=d.get("description", ""),
            file=d.get("file", ""),
            builtin=d.get("builtin", False),
            hud=d.get("hud", {}),
            css=css,
        )


# ============================================================
# 颜色选择按钮
# ============================================================

class ColorPickerButton(QPushButton):
    """可点击选色的按钮，显示当前颜色预览"""

    color_changed = Signal(str)

    def __init__(self, label: str, hex_color: str = "#000000FF", parent=None):
        super().__init__(parent)
        self.label_text = label
        self._color = hex_color
        self.setMinimumSize(80, 28)
        self.setCursor(Qt.PointingHandCursor)
        self._update_style()
        self.clicked.connect(self._pick_color)

    def _update_style(self):
        qc = hex_to_qcolor(self._color)
        self.setStyleSheet(f"""
            ColorPickerButton {{
                background-color: {self._color};
                border: 1px solid #CCCCCC;
                border-radius: 4px;
                color: {self._contrast_color(qc)};
                padding: 4px 8px;
                font-size: 12px;
            }}
            ColorPickerButton:hover {{
                border: 1px solid #666666;
            }}
        """)
        self.setText(f"{self.label_text}  {self._color}")

    @staticmethod
    def _contrast_color(qc: QColor) -> str:
        luminance = (0.299 * qc.red() + 0.587 * qc.green() + 0.114 * qc.blue()) / 255
        alpha = qc.alpha()
        if alpha < 128:
            return "#333333"
        return "#FFFFFF" if luminance < 0.5 else "#333333"

    def _pick_color(self):
        qc = hex_to_qcolor(self._color)
        new_color = QColorDialog.getColor(qc, self, f"选择 {self.label_text} 颜色",
                                          QColorDialog.ShowAlphaChannel)
        if new_color.isValid():
            self._color = qcolor_to_hex(new_color)
            self._update_style()
            self.color_changed.emit(self._color)

    def color(self) -> str:
        return self._color

    def set_color(self, hex_color: str):
        self._color = hex_color
        self._update_style()


# ============================================================
# HUD 颜色配置面板
# ============================================================

class HudColorsPanel(QGroupBox):
    """HUD 7 种颜色配置面板"""

    changed = Signal()

    def __init__(self, parent=None):
        super().__init__("HUD 颜色配置", parent)
        self._buttons = {}
        self._setup_ui(DEFAULT_HUD)

    def _setup_ui(self, hud: dict):
        layout = QGridLayout(self)
        layout.setSpacing(8)
        layout.setContentsMargins(12, 16, 12, 12)

        for i, (key, label) in enumerate(HUD_FIELDS):
            lbl = QLabel(f"{label}:")
            lbl.setFixedWidth(90)
            lbl.setAlignment(Qt.AlignRight | Qt.AlignVCenter)
            btn = ColorPickerButton(label, hud.get(key, "#000000FF"))
            btn.color_changed.connect(lambda _c, k=key: self._on_color_changed(k, _c))
            self._buttons[key] = btn
            row = i // 2
            col = (i % 2) * 2
            layout.addWidget(lbl, row, col)
            layout.addWidget(btn, row, col + 1)

        btn_row = (len(HUD_FIELDS) + 1) // 2
        preset_layout = QHBoxLayout()
        preset_label = QLabel("快速套用:")
        preset_label.setStyleSheet("color: #666;")
        preset_layout.addWidget(preset_label)

        for style_id, style_data in BUILTIN_STYLES.items():
            btn = QPushButton(style_data["name"])
            btn.setFixedHeight(24)
            btn.setCursor(Qt.PointingHandCursor)
            btn.setStyleSheet("""
                QPushButton { border: 1px solid #CBD5E1; border-radius: 12px;
                              background: #F8FAFC; padding: 0 12px; color: #334155;
                              font-size: 11px; }
                QPushButton:hover { background: #E2E8F0; border-color: #6366F1; color: #4F46E5; }
            """)
            btn.clicked.connect(lambda _c=False, sid=style_id: self._apply_preset(sid))
            preset_layout.addWidget(btn)
        preset_layout.addStretch()

        preset_widget = QWidget()
        preset_widget.setLayout(preset_layout)
        layout.addWidget(preset_widget, btn_row, 0, 1, 4)

    def _on_color_changed(self, key: str, hex_color: str):
        self.changed.emit()

    def _apply_preset(self, style_id: str):
        if style_id not in BUILTIN_STYLES:
            return
        hud = BUILTIN_STYLES[style_id]["hud"]
        for key, btn in self._buttons.items():
            btn.set_color(hud.get(key, "#000000FF"))
        self.changed.emit()

    def get_hud(self) -> dict:
        return {k: b.color() for k, b in self._buttons.items()}

    def set_hud(self, hud: dict):
        for key, btn in self._buttons.items():
            if key in hud:
                btn.set_color(hud[key])


# ============================================================
# 样式编辑对话框
# ============================================================

class StyleEditDialog(QDialog):
    """编辑单个样式的对话框"""

    def __init__(self, style: Optional[StyleEntry] = None, parent=None):
        super().__init__(parent)
        self.setWindowTitle("编辑样式" if style else "新增样式")
        self.setMinimumSize(520, 580)
        self.setWindowFlags(self.windowFlags() | Qt.WindowMinMaxButtonsHint)

        self._style = style or StyleEntry()
        self._setup_ui()
        if style:
            self._load_style(style)

    def _setup_ui(self):
        layout = QVBoxLayout(self)
        layout.setSpacing(12)
        layout.setContentsMargins(16, 16, 16, 16)

        info_group = QGroupBox("基本信息")
        info_form = QFormLayout(info_group)
        info_form.setSpacing(8)

        self.id_edit = QLineEdit()
        self.id_edit.setPlaceholderText("英文小写+下划线，如 neon_style")
        self.id_edit.setToolTip("样式唯一标识符，资源包中的 id 字段")
        info_form.addRow("样式 ID*:", self.id_edit)

        self.name_edit = QLineEdit()
        self.name_edit.setPlaceholderText("显示名称，如 霓虹风格")
        info_form.addRow("显示名称:", self.name_edit)

        self.desc_edit = QLineEdit()
        self.desc_edit.setPlaceholderText("样式描述（可选）")
        info_form.addRow("描述:", self.desc_edit)

        self.file_edit = QLineEdit()
        self.file_edit.setPlaceholderText("styles/xxx.css")
        self.file_edit.setToolTip("CSS 文件相对 templates/ 的路径")
        info_form.addRow("CSS 文件*:", self.file_edit)

        self.builtin_check = QCheckBox("内置样式")
        self.builtin_check.setToolTip("资源包样式应设为非内置")
        info_form.addRow("内置:", self.builtin_check)

        layout.addWidget(info_group)

        self.hud_panel = HudColorsPanel()
        layout.addWidget(self.hud_panel, 1)

        self.preview_label = QLabel("HUD 预览")
        self.preview_label.setObjectName("previewLabel")
        self.preview_label.setAlignment(Qt.AlignCenter)
        layout.addWidget(self.preview_label)

        self.hud_preview = QFrame()
        self.hud_preview.setObjectName("hudPreview")
        self.hud_preview.setFixedSize(280, 80)
        self.hud_preview.setCursor(Qt.PointingHandCursor)
        self.hud_preview_layout = QVBoxLayout(self.hud_preview)
        self.hud_preview_layout.setContentsMargins(12, 8, 12, 8)
        self.hud_preview_layout.setSpacing(2)
        self.preview_title = QLabel("纪元 1 年 1 月")
        self.preview_title.setFont(QFont("Microsoft YaHei", 11, QFont.Bold))
        self.preview_day = QLabel("1 日 星期一")
        self.preview_day.setFont(QFont("Microsoft YaHei", 9))
        self.preview_event = QLabel("◆ 特殊日子")
        self.preview_event.setFont(QFont("Microsoft YaHei", 9))
        self.hud_preview_layout.addWidget(self.preview_title)
        self.hud_preview_layout.addWidget(self.preview_day)
        self.hud_preview_layout.addWidget(self.preview_event)
        self.hud_panel.changed.connect(self._update_preview)
        layout.addWidget(self.hud_preview, alignment=Qt.AlignCenter)

        button_box = QDialogButtonBox(
            QDialogButtonBox.Ok | QDialogButtonBox.Cancel
        )
        button_box.button(QDialogButtonBox.Ok).setText("保存")
        button_box.button(QDialogButtonBox.Cancel).setText("取消")
        button_box.accepted.connect(self._accept)
        button_box.rejected.connect(self.reject)
        layout.addWidget(button_box)

        # 关键：给 StyleEditDialog 整体指定纯色安全背景，避免 Windows 下透明继承变全黑
        _apply_dialog_safe_style(self, safe_bg_palette=parent)

        self._update_preview()

    def _load_style(self, style: StyleEntry):
        self.id_edit.setText(style.id)
        self.name_edit.setText(style.name)
        self.desc_edit.setText(style.description)
        self.file_edit.setText(style.file)
        self.builtin_check.setChecked(style.builtin)
        if style.hud:
            self.hud_panel.set_hud(style.hud)

    def _update_preview(self):
        hud = self.hud_panel.get_hud()
        body = hud.get("body", "#F7F7F8E8")
        decor = hud.get("decor", "#E4E7ECFF")
        primary = hud.get("textPrimary", "#1E293BFF")
        secondary = hud.get("textSecondary", "#475569FF")
        event = hud.get("textEvent", "#B45309FF")
        border = hud.get("border", "#0000001A")

        self.hud_preview.setStyleSheet(f"""
            QFrame {{
                background-color: {body};
                border: 1px solid;
                border-color: {border};
                border-radius: 8px;
            }}
        """)
        self.preview_title.setStyleSheet(f"color: {primary}; background: transparent;")
        self.preview_day.setStyleSheet(f"color: {secondary}; background: transparent;")
        self.preview_event.setStyleSheet(f"color: {event}; background: transparent;")

        decor_strip = QLabel()
        decor_strip.setFixedHeight(3)
        decor_strip.setStyleSheet(f"background-color: {decor}; border-radius: 2px;")
        if self.hud_preview_layout.itemAt(0).widget() and not isinstance(
                self.hud_preview_layout.itemAt(0).widget(), QLabel):
            self.hud_preview_layout.insertWidget(0, decor_strip)
        else:
            self.hud_preview_layout.insertWidget(0, decor_strip)
        self._decor_strip = decor_strip

    def _accept(self):
        id_ = self.id_edit.text().strip()
        file_ = self.file_edit.text().strip()
        if not id_:
            dlg_warn(self, "输入错误", "样式 ID 不能为空")
            return
        if not file_:
            dlg_warn(self, "输入错误", "CSS 文件路径不能为空")
            return
        if not re.match(r'^[a-z][a-z0-9_]*$', id_):
            dlg_warn(self, "输入错误",
                     "样式 ID 必须以小写字母开头，只含小写字母、数字和下划线")
            return

        # 关键点：对话框不编辑 CSS，但要保留原 StyleEntry 已有的 css 字段（别丢了）
        prev_css = getattr(self._style, "css", "") or ""
        self._style = StyleEntry(
            id_=id_,
            name=self.name_edit.text().strip() or id_,
            description=self.desc_edit.text().strip(),
            file_=file_,
            builtin=self.builtin_check.isChecked(),
            hud=self.hud_panel.get_hud(),
            css=prev_css,
        )
        self.accept()

    def get_style(self) -> StyleEntry:
        return self._style


# ============================================================
# 主题系统 —— 6 色调 × 亮/暗 动态生成
# ============================================================

# 每种色调定义：名称、主渐变起止、选中渐变起止、模板按钮渐变
PALETTES = {
    "slate": {
        "name": "经典灰白",          # 工具默认：中性 slate 灰阶，没有彩色干扰
        "primary_start": "#94A3B8",  # slate-400：主渐变起点（淡灰蓝）
        "primary_end": "#64748B",    # slate-500：主渐变终点（中性灰）
        "accent": "#475569",         # slate-600：主按钮底色
        "accent_hover": "#334155",   # slate-700：主按钮悬停
        "accent_light": "#CBD5E1",   # slate-300：主按钮浅高亮 / 边框浅
        "sel_start": "#94A3B8",      # 列表项选中渐变起点
        "sel_end": "#64748B",        # 列表项选中渐变终点
        "edit_start": "#64748B",     # 「编辑」按钮：深灰
        "edit_end": "#475569",
        "tpl_start": "#64748B",      # 「模板」按钮：中性灰（整工具灰白无彩色）
        "tpl_end": "#475569",
    },
    "indigo": {
        "name": "靛蓝",
        "primary_start": "#6366F1",
        "primary_end": "#8B5CF6",
        "accent": "#6366F1",
        "accent_hover": "#4F46E5",
        "accent_light": "#A5B4FC",
        "sel_start": "#6366F1",
        "sel_end": "#8B5CF6",
        "edit_start": "#0EA5E9",
        "edit_end": "#3B82F6",
        "tpl_start": "#EC4899",
        "tpl_end": "#F472B6",
    },
    "ocean": {
        "name": "海洋蓝",
        "primary_start": "#0EA5E9",
        "primary_end": "#2563EB",
        "accent": "#0284C7",
        "accent_hover": "#0369A1",
        "accent_light": "#7DD3FC",
        "sel_start": "#0EA5E9",
        "sel_end": "#3B82F6",
        "edit_start": "#06B6D4",
        "edit_end": "#0891B2",
        "tpl_start": "#F97316",
        "tpl_end": "#FB923C",
    },
    "forest": {
        "name": "森林绿",
        "primary_start": "#10B981",
        "primary_end": "#059669",
        "accent": "#059669",
        "accent_hover": "#047857",
        "accent_light": "#6EE7B7",
        "sel_start": "#10B981",
        "sel_end": "#059669",
        "edit_start": "#14B8A6",
        "edit_end": "#0D9488",
        "tpl_start": "#F59E0B",
        "tpl_end": "#FBBF24",
    },
    "mystic": {
        "name": "幻境紫",
        "primary_start": "#8B5CF6",
        "primary_end": "#C026D3",
        "accent": "#7C3AED",
        "accent_hover": "#6D28D9",
        "accent_light": "#C4B5FD",
        "sel_start": "#8B5CF6",
        "sel_end": "#D946EF",
        "edit_start": "#6366F1",
        "edit_end": "#8B5CF6",
        "tpl_start": "#F43F5E",
        "tpl_end": "#FB7185",
    },
    "sunset": {
        "name": "日落橙",
        "primary_start": "#F59E0B",
        "primary_end": "#EF4444",
        "accent": "#D97706",
        "accent_hover": "#B45309",
        "accent_light": "#FCD34D",
        "sel_start": "#F59E0B",
        "sel_end": "#F43F5E",
        "edit_start": "#FB923C",
        "edit_end": "#F97316",
        "tpl_start": "#8B5CF6",
        "tpl_end": "#A78BFA",
    },
    "sakura": {
        "name": "樱花粉",
        "primary_start": "#EC4899",
        "primary_end": "#F472B6",
        "accent": "#DB2777",
        "accent_hover": "#BE185D",
        "accent_light": "#F9A8D4",
        "sel_start": "#EC4899",
        "sel_end": "#F472B6",
        "edit_start": "#A855F7",
        "edit_end": "#C084FC",
        "tpl_start": "#10B981",
        "tpl_end": "#34D399",
    },
}


def build_theme(palette_id: str, is_dark: bool) -> str:
    """根据色调 id 和 亮/暗模式 动态生成完整 QSS 样式表。

    原理：
      1. 从 PALETTES 取出该色调的渐变色定义；
      2. 根据 is_dark 切换所有背景色、边框色、文字色的明暗基调配色；
      3. 所有强调按钮（新增/编辑/删除/重置/导出/模板）都使用该色调的渐变色；
      4. 工具栏、GroupBox 标题、Tab 选中项、列表选中项使用线性渐变。
    """
    p = PALETTES.get(palette_id, PALETTES["indigo"])
    p_start, p_end = p["primary_start"], p["primary_end"]
    p_hover_start, p_hover_end = p["accent_hover"], p["primary_end"]
    accent = p["accent"]
    accent_hover = p["accent_hover"]
    accent_light = p["accent_light"]
    sel_start, sel_end = p["sel_start"], p["sel_end"]
    edit_start, edit_end = p["edit_start"], p["edit_end"]
    tpl_start, tpl_end = p["tpl_start"], p["tpl_end"]

    # 把主色调转换成极淡的 tint，用于主背景色晕，确保切换色调时背景有明显视觉变化
    # 原理：将 primary_start 的 HSL 保持色相，把饱和度压到 15-25%、亮度提到 96-98% 左右
    # 这里用简单的通道混合模拟：#RRGGBB 与 #FFFFFF / #1E293B 按 1:8 比例插值
    def _mix(a_hex: str, b_hex: str, ratio: float) -> str:
        ar, ag, ab = int(a_hex[1:3], 16), int(a_hex[3:5], 16), int(a_hex[5:7], 16)
        br, bg, bb = int(b_hex[1:3], 16), int(b_hex[3:5], 16), int(b_hex[5:7], 16)
        return "#{:02X}{:02X}{:02X}".format(
            int(ar * ratio + br * (1 - ratio)),
            int(ag * ratio + bg * (1 - ratio)),
            int(ab * ratio + bb * (1 - ratio)),
        )

    if is_dark:
        # 暗色基调配色
        bg_window = "#0F172A"           # 主窗口背景（纯）
        # 暗色模式：主色调与 #111827 按 1:4 混合 → 深色色调 tint
        tint_deep = _mix(p_start, "#111827", 0.35)     # 面板内部渐变顶色
        tint_mid = _mix(p_start, "#0F172A", 0.2)       # 面板内部渐变中色
        tint_soft = _mix(p_start, "#1E293B", 0.12)     # 工具栏渐变色
        tint_verysoft = _mix(p_start, "#0F172A", 0.08) # 主窗口对角渐变终点色

        bg_panel = "#1E293B"            # 面板(工具栏/状态栏/GroupBox)
        bg_elevated = "#334155"         # 抬高面（Tab 未选中 / GroupBox 标题）
        bg_input = "#0F172A"            # 输入框背景
        bg_hover = "#334155"            # hover 背景
        bg_pressed = "#475569"          # pressed 背景
        border = "#334155"              # 常规边框
        border_strong = "#475569"       # 强边框
        text_primary = "#E2E8F0"        # 主要文字
        text_secondary = "#CBD5E1"      # 次要文字
        text_muted = "#94A3B8"          # 弱化文字
        list_item_bg = "#1E293B"
        list_hover = "#334155"
        tab_pane = "#1E293B"
        tab_unsel = "#334155"
        tab_unsel_border = "#475569"
        tab_sel = "#1E293B"
        scrollbar_bg = "#1E293B"
        scrollbar_handle = "#475569"
        scrollbar_handle_hover = "#64748B"
        checkbox_border = "#475569"
        checkbox_bg = "#0F172A"
        hud_prev_bg = "#1E293B"
        hud_prev_border = "#334155"
        # 暗色模式背景联动色调：
        # 工具栏：浅深色 → 深色色调 tint（顶部到底部渐变，带色晕）
        gradient_toolbar = f"qlineargradient(x1:0,y1:0,x2:1,y2:0, stop:0 #1E293B, stop:0.5 {tint_soft}, stop:1 #0F172A)"
        gradient_groupbox_title = f"qlineargradient(x1:0,y1:0,x2:1,y2:0, stop:0 #334155, stop:0.5 {tint_deep}, stop:1 #1E293B)"
        # GroupBox 面板内部：顶部带色调色晕 → 下方面板色
        gradient_panel_inner = f"qlineargradient(x1:0,y1:0,x2:0,y2:1, stop:0 {tint_deep}, stop:0.15 {tint_mid}, stop:1 #1E293B)"
        # 主窗口：左上纯黑灰 → 右下带色调的深灰（对角渐变）
        gradient_window = f"qlineargradient(x1:0,y1:0,x2:1,y2:1, stop:0 #0F172A, stop:1 {tint_verysoft})"
        # Tab 选中时的上侧色晕
        gradient_tab_sel = f"qlineargradient(x1:0,y1:0,x2:0,y2:1, stop:0 {tint_mid}, stop:1 #1E293B)"
    else:
        # 亮色基调配色
        # 亮色模式：主色调与 #FFFFFF 按 1:8 混合 → 极淡色晕
        tint_deep = _mix(p_start, "#FFFFFF", 0.22)     # GroupBox 顶部色晕
        tint_mid = _mix(p_start, "#FFFFFF", 0.15)      # 面板中色
        tint_soft = _mix(p_start, "#FFFFFF", 0.10)     # 工具栏渐变色
        tint_verysoft = _mix(p_start, "#FFFFFF", 0.06) # 主窗口对角渐变终点色

        bg_window = "#F8FAFC"
        bg_panel = "#FFFFFF"
        bg_elevated = "#F1F5F9"
        bg_input = "#FFFFFF"
        bg_hover = "#F1F5F9"
        bg_pressed = "#E2E8F0"
        border = "#E2E8F0"
        border_strong = "#CBD5E1"
        text_primary = "#1E293B"
        text_secondary = "#334155"
        text_muted = "#64748B"
        list_item_bg = "#FFFFFF"
        list_hover = "#F1F5F9"
        tab_pane = "#FFFFFF"
        tab_unsel = "#F1F5F9"
        tab_unsel_border = "#E2E8F0"
        tab_sel = "#FFFFFF"
        scrollbar_bg = "#F1F5F9"
        scrollbar_handle = "#CBD5E1"
        scrollbar_handle_hover = "#94A3B8"
        checkbox_border = "#CBD5E1"
        checkbox_bg = "#FFFFFF"
        hud_prev_bg = "#F8FAFC"
        hud_prev_border = "#E2E8F0"
        # 亮色模式背景联动色调（最关键：切色调时肉眼一眼能看出区别）：
        # 工具栏：白 → 淡色调色 → 浅灰（水平渐变），比如靛蓝时是白带淡紫，森林是白带淡绿
        gradient_toolbar = f"qlineargradient(x1:0,y1:0,x2:1,y2:0, stop:0 #FFFFFF, stop:0.5 {tint_soft}, stop:1 {bg_elevated})"
        gradient_groupbox_title = f"qlineargradient(x1:0,y1:0,x2:1,y2:0, stop:0 {bg_elevated}, stop:0.4 {tint_mid}, stop:1 #FFFFFF)"
        # GroupBox 面板内部：顶部带明显色晕 → 底部纯白（用户看面板就能区分色调）
        gradient_panel_inner = f"qlineargradient(x1:0,y1:0,x2:0,y2:1, stop:0 {tint_deep}, stop:0.18 {tint_mid}, stop:1 #FFFFFF)"
        # 主窗口：左上纯白 → 右下带极淡色调的白（对角渐变），增加整体质感
        gradient_window = f"qlineargradient(x1:0,y1:0,x2:1,y2:1, stop:0 #FFFFFF, stop:1 {tint_verysoft})"
        # Tab 选中时的上侧色晕
        gradient_tab_sel = f"qlineargradient(x1:0,y1:0,x2:0,y2:1, stop:0 {tint_mid}, stop:1 #FFFFFF)"

    text_sel_fg = "#FFFFFF"
    text_accent = accent
    text_accent_light = accent_light if is_dark else accent

    return f"""
/* ===== 根容器：带色调 tint 的对角渐变 =====
   注意：这里不能用"QWidget"通配后代选择器（会导致 QMessageBox/QInputDialog 内部小
   部件 / QLineEdit 内部 / QComboBox 下拉 viewport 全部被强行 transparent → Windows
   下 Qt 没有背景像素可绘制就 fallback 成纯黑 = 用户截图里的全黑窗口）。            */
QMainWindow,
QWidget#centralWidget {{
    background: {gradient_window};
    color: {text_primary};
    font-family: "Microsoft YaHei", "Segoe UI", sans-serif;
    font-size: 13px;
}}
/* ===== 只对已知的"纯布局父容器"设透明 =====
   范围严格限制：直接子级且只参与布局分组、本身不做绘制的容器 widget。
   绝对不能波及 QLineEdit / QPushButton / QComboBox viewport / QMessageBox 内部。  */
QSplitter > QWidget,                 /* 左右分栏的左右两半容器 */
QTabWidget QStackedWidget > QWidget, /* Tab 页的根页容器 */
QScrollArea > QWidget > QWidget,     /* ScrollArea 内部承载页 */
QMainWindow > QWidget#centralWidget  /* 主窗口最外层 central（双保险） */
{{
    background: transparent;
}}

QToolBar {{
    background: {gradient_toolbar};
    border: none;
    border-bottom: 1px solid {border};
    padding: 6px 10px;
    spacing: 4px;
}}

QToolBar QToolButton {{
    background: transparent;
    border: 1px solid transparent;
    border-radius: 6px;
    padding: 6px 14px;
    color: {text_secondary};
    font-weight: 500;
}}
QToolBar QToolButton:hover {{
    background: {bg_hover};
    border: 1px solid {border_strong};
    color: {text_primary};
}}
QToolBar QToolButton:pressed {{
    background: {bg_pressed};
}}

/* 工具栏中的下拉框 */
QToolBar QComboBox {{
    background: {bg_input};
    border: 1px solid {border_strong};
    border-radius: 6px;
    padding: 4px 10px;
    color: {text_primary};
    min-width: 110px;
}}
QToolBar QComboBox:hover {{
    border-color: {accent};
}}
QToolBar QComboBox::drop-down {{
    border: none;
    width: 20px;
}}
QToolBar QComboBox QAbstractItemView {{
    background: {bg_panel};
    border: 1px solid {border};
    color: {text_primary};
    selection-background-color: {accent};
    selection-color: {text_sel_fg};
    outline: 0;
}}

QStatusBar {{
    background: {bg_panel};
    border-top: 1px solid {border};
    color: {text_muted};
    padding: 4px 12px;
}}
QStatusBar QLabel {{
    color: {text_muted};
}}

QGroupBox {{
    /* 面板内部垂直渐变：顶部带色调色晕 → 底部纯色，肉眼一眼区分色调 */
    background: {gradient_panel_inner};
    border: 1px solid {border};
    border-radius: 10px;
    margin-top: 14px;
    padding: 16px 12px 12px 12px;
    font-weight: 600;
    color: {text_primary};
}}
/* ====== GroupBox 内部不要做 transparent 通配 ======
   之前的 "QGroupBox QWidget {{ background: transparent; }}" 会把
   QLineEdit / QPushButton / ColorPickerButton 内部 / QCheckBox 指示框 等内容控件
   也设为透明，在 Windows 下没有父级像素时就会 fallback 成黑底。
   正确做法：仅对 GroupBox 自身设置渐变背景，内容控件使用各自的 QSS 规则。     */
QGroupBox::title {{
    subcontrol-origin: margin;
    subcontrol-position: top left;
    left: 14px;
    padding: 4px 10px;
    background: {gradient_groupbox_title};
    border: 1px solid {border};
    border-radius: 6px;
    color: {text_secondary};
}}

/* 通用按钮 */
QPushButton {{
    background: {bg_panel};
    border: 1px solid {border_strong};
    border-radius: 8px;
    padding: 6px 16px;
    color: {text_secondary};
    font-weight: 500;
    min-height: 20px;
}}
QPushButton:hover {{
    background: {bg_hover};
    border-color: {accent};
    color: {text_primary};
}}
QPushButton:pressed {{
    background: {bg_pressed};
}}

/* ====== 特殊按钮：渐变样式 ====== */
/* 新增按钮：主色调渐变 */
QPushButton#btnAdd {{
    background: qlineargradient(x1:0,y1:0,x2:1,y2:1, stop:0 {p_start}, stop:1 {p_end});
    border: none;
    color: white;
    font-weight: 600;
}}
QPushButton#btnAdd:hover {{
    background: qlineargradient(x1:0,y1:0,x2:1,y2:1, stop:0 {p_hover_start}, stop:1 {p_hover_end});
}}

/* 编辑按钮：编辑色渐变 */
QPushButton#btnEdit {{
    background: qlineargradient(x1:0,y1:0,x2:1,y2:1, stop:0 {edit_start}, stop:1 {edit_end});
    border: none;
    color: white;
    font-weight: 600;
}}
QPushButton#btnEdit:hover {{
    background: qlineargradient(x1:0,y1:0,x2:1,y2:1, stop:0 {accent_hover}, stop:1 {edit_end});
}}

/* 套用模板按钮：独立渐变色（与编辑按钮区分，解决白字白背景） */
QPushButton#btnTemplate {{
    background: qlineargradient(x1:0,y1:0,x2:1,y2:1, stop:0 {tpl_start}, stop:1 {tpl_end});
    border: none;
    color: white;
    font-weight: 600;
}}
QPushButton#btnTemplate:hover {{
    background: qlineargradient(x1:0,y1:0,x2:1,y2:1, stop:0 {accent_hover}, stop:1 {tpl_end});
}}

/* 删除按钮：红色渐变 */
QPushButton#btnDelete {{
    background: qlineargradient(x1:0,y1:0,x2:1,y2:1, stop:0 #F43F5E, stop:1 #EF4444);
    border: none;
    color: white;
    font-weight: 600;
}}
QPushButton#btnDelete:hover {{ background: qlineargradient(x1:0,y1:0,x2:1,y2:1, stop:0 #E11D48, stop:1 #DC2626); }}

/* 重置按钮：橙色渐变 */
QPushButton#btnReset {{
    background: qlineargradient(x1:0,y1:0,x2:1,y2:1, stop:0 #F59E0B, stop:1 #D97706);
    border: none;
    color: white;
    font-weight: 600;
}}
QPushButton#btnReset:hover {{ background: qlineargradient(x1:0,y1:0,x2:1,y2:1, stop:0 #D97706, stop:1 #B45309); }}

/* 导出按钮：绿色渐变 */
QPushButton#btnExport {{
    background: qlineargradient(x1:0,y1:0,x2:1,y2:1, stop:0 #10B981, stop:1 #059669);
    border: none;
    color: white;
    font-weight: 600;
}}
QPushButton#btnExport:hover {{ background: qlineargradient(x1:0,y1:0,x2:1,y2:1, stop:0 #059669, stop:1 #047857); }}

/* 样式列表 */
QListWidget {{
    background: {list_item_bg};
    border: 1px solid {border};
    border-radius: 10px;
    padding: 6px;
    outline: none;
}}
QListWidget::item {{
    padding: 10px 14px;
    border-radius: 6px;
    margin: 2px 4px;
    color: {text_secondary};
}}
QListWidget::item:hover {{
    background: {list_hover};
}}
QListWidget::item:selected {{
    background: qlineargradient(x1:0,y1:0,x2:1,y2:1, stop:0 {sel_start}, stop:1 {sel_end});
    color: white;
    border: none;
}}

/* Tab 系统 */
QTabBar::tab {{
    background: {tab_unsel};
    border: 1px solid {tab_unsel_border};
    border-bottom: none;
    border-top-left-radius: 8px;
    border-top-right-radius: 8px;
    padding: 8px 20px;
    margin-right: 2px;
    color: {text_muted};
    font-weight: 500;
}}
QTabBar::tab:selected {{
    /* 选中 Tab 顶部带色调色晕，与工具栏/面板色晕保持一致 */
    background: {gradient_tab_sel};
    color: {text_accent};
    border-bottom: 2px solid {accent};
    margin-bottom: -1px;
}}
/* Tab 内容区（pane）也要带顶部色晕渐变，不再死白 */
QTabWidget::pane {{
    background: {gradient_panel_inner};
    border: 1px solid {border};
    border-radius: 10px;
    top: -1px;
}}
QTabBar::tab:hover:!selected {{
    background: {bg_hover};
    color: {text_primary};
}}

/* 输入控件 */
QLineEdit, QSpinBox, QComboBox {{
    background: {bg_input};
    border: 1px solid {border_strong};
    border-radius: 6px;
    padding: 6px 10px;
    color: {text_primary};
    selection-background-color: {accent};
    selection-color: {text_sel_fg};
}}
QLineEdit:focus, QSpinBox:focus, QComboBox:focus {{
    border-color: {accent};
    border-width: 2px;
    padding: 5px 9px;
}}
QComboBox QAbstractItemView {{
    background: {bg_panel};
    border: 1px solid {border};
    color: {text_primary};
    selection-background-color: {accent};
    selection-color: {text_sel_fg};
    outline: 0;
}}

QTextEdit, QPlainTextEdit {{
    background: {bg_input};
    border: 1px solid {border};
    border-radius: 8px;
    padding: 10px;
    color: {text_primary};
    selection-background-color: {accent};
    selection-color: {text_sel_fg};
}}
QTextEdit:focus, QPlainTextEdit:focus {{
    border-color: {accent};
}}

QCheckBox {{
    spacing: 8px;
    color: {text_secondary};
}}
QCheckBox::indicator {{
    width: 18px;
    height: 18px;
    border-radius: 4px;
    border: 2px solid {checkbox_border};
    background: {checkbox_bg};
}}
QCheckBox::indicator:checked {{
    background: qlineargradient(x1:0,y1:0,x2:1,y2:1, stop:0 {sel_start}, stop:1 {sel_end});
    border-color: {accent};
    image: none;
}}

QLabel {{
    color: {text_secondary};
}}
QLabel#listHeader {{
    font-size: 15px;
    font-weight: 700;
    color: {text_primary};
    padding: 4px 2px;
}}
QLabel#previewLabel {{
    font-size: 14px;
    font-weight: 600;
    color: {text_accent};
    padding: 6px;
}}

QSplitter::handle {{
    background: {border};
    width: 2px;
}}
QSplitter::handle:hover {{
    background: {accent};
}}

/* 滚动条 */
QScrollBar:vertical {{
    background: {scrollbar_bg};
    width: 10px;
    border-radius: 5px;
}}
QScrollBar::handle:vertical {{
    background: {scrollbar_handle};
    border-radius: 5px;
    min-height: 30px;
}}
QScrollBar::handle:vertical:hover {{
    background: {scrollbar_handle_hover};
}}
QScrollBar::add-line:vertical, QScrollBar::sub-line:vertical {{
    height: 0;
}}
QScrollBar:horizontal {{
    background: {scrollbar_bg};
    height: 10px;
    border-radius: 5px;
}}
QScrollBar::handle:horizontal {{
    background: {scrollbar_handle};
    border-radius: 5px;
    min-width: 30px;
}}
QScrollBar::handle:horizontal:hover {{
    background: {scrollbar_handle_hover};
}}
QScrollBar::add-line:horizontal, QScrollBar::sub-line:horizontal {{
    width: 0;
}}

QFrame#hudPreview {{
    background: {hud_prev_bg};
    border: 2px solid {hud_prev_border};
    border-radius: 12px;
}}

/* ===== 子对话框（用户截图里的黑窗）：使用纯色安全背景，严禁 transparent =====
   QDialog / QMessageBox / QInputDialog 都是"顶层弹出窗口"，没有父容器背景像素
   供合成，如果把内部 QWidget 设 transparent，Windows 下 Qt 会直接画黑色底。
   所以这里一律用纯色 {bg_panel} 显式指定，保证肉眼可读。                        */
QDialog,
QMessageBox,
QInputDialog {{
    background: {bg_panel};
}}
/* 弹出窗口内部的 QLabel/QPushButton/QLineEdit 等都必须有显式背景或继承父纯色 */
QMessageBox QLabel, QInputDialog QLabel {{
    color: {text_primary};
    background: transparent;  /* ← OK：父级已经是 bg_panel 纯色，不需要再透明父 */
}}

QDialogButtonBox {{
    background: transparent;
}}
QDialogButtonBox QPushButton {{
    min-width: 80px;
}}

/* QComboBox 的下拉列表（QAbstractItemView 弹出视图）：必须纯色，否则透明+黑 */
QComboBox QAbstractItemView {{
    background: {bg_panel};
    color: {text_primary};
    border: 1px solid {border};
    selection-background-color: {accent};
    selection-color: {text_sel_fg};
    outline: 0;
}}

/* ===================== viewport 透明规则（修正版） =====================
   错误做法：QAbstractScrollArea QWidget {{ background: transparent; }}
       这是"后代选择器"，会把 ListWidget 内部的 item / TextEdit 文本区 /
       QComboBox 下拉内部 widget 全部透明，透明叠加 → Windows 下变黑。
   正确做法：用 Qt 官方的 "::viewport" 伪状态选择器，只选中
       scroll area 的那个专门的背景 viewport widget，不波及内容。             */
QAbstractScrollArea {{
    background: transparent;  /* 外层边框区域透明：把父容器（GroupBox/Tab）的色晕透出来 */
}}
QAbstractScrollArea::viewport {{
    /* 只有 viewport 自身透明，内容控件（QListWidget 本身）在上方已单独设
       {{list_item_bg}} / {{bg_input}}，优先级更高，不会被这里覆盖。             */
    background: transparent;
}}
/* 内容控件兜底：明确告诉 Qt，我自己有背景色，不要按 viewport 透明合成 */
QListWidget,
QTextEdit,
QPlainTextEdit,
QLineEdit,
QComboBox,
QSpinBox {{
    background-clip: padding;
}}
/* StatusBar 背景也联动一下色晕，不再死白/死灰 */
QStatusBar {{
    background: {gradient_tab_sel};
}}
/* Splitter 分界线在亮色模式更柔和，暗色更突出 */
QSplitter::handle:hover {{
    background: qlineargradient(x1:0,y1:0,x2:1,y2:0, stop:0 {sel_start}, stop:1 {sel_end});
}}
"""


# ============================================================
# 主窗口
# ============================================================

class ResourcePackGenerator(QMainWindow):
    """资源包生成器主窗口"""

    def __init__(self):
        super().__init__()
        self.setWindowTitle("Calendar Mod 资源包生成器")
        self.setMinimumSize(1100, 750)
        self.resize(1280, 820)

        self.styles: list[StyleEntry] = []
        self.pack_format = 15
        self.pack_description = "我的日历自定义样式"
        self._current_index = -1
        self._dark_theme = False
        self._palette_id = "slate"   # 当前色调：工具默认——经典灰白（中性灰阶）

        self._apply_theme()
        self._setup_ui()
        self._setup_toolbar()
        self._setup_statusbar()
        self._load_builtin_styles()

    # ---------- 主题系统 ----------

    def _apply_theme(self):
        """统一主题应用入口：根据当前色调+明暗生成并应用 QSS"""
        app = QApplication.instance()
        qss = build_theme(self._palette_id, self._dark_theme)
        app.setStyleSheet(qss)
        # ===== 关键：强制全层级重绘 =====
        # Windows 下 Qt 对 QSS 渐变有缓存，只 setStyleSheet 可能出现局部不刷新（用户感觉"背景没变"）。
        # 这里用 processEvents + update + polish/unpolish + repaint 的组合拳确保所有控件真正重绘。
        from PySide6.QtWidgets import QStyle
        QApplication.processEvents()
        try:
            self.style().unpolish(self)
            self.style().polish(self)
        except Exception:
            pass
        self.update()
        self.repaint()
        # 递归刷新所有子控件的几何与背景（尤其是 QTabWidget/GroupBox 内部）
        for child in self.findChildren(QWidget):
            try:
                child.style().unpolish(child)
                child.style().polish(child)
            except Exception:
                pass
            child.update()
        QApplication.processEvents()

    def _toggle_theme(self):
        """切换 亮 / 暗 模式"""
        self._dark_theme = not self._dark_theme
        self._apply_theme()
        if hasattr(self, '_theme_action'):
            self._theme_action.setText("☀️ 亮色模式" if self._dark_theme else "🌙 暗色模式")
        palette_name = PALETTES.get(self._palette_id, PALETTES["indigo"])["name"]
        mode = "暗色" if self._dark_theme else "亮色"
        self.statusBar().showMessage(f"已切换到 {palette_name} · {mode} 模式")

    def _on_palette_changed(self, index: int):
        """色调下拉框切换回调"""
        ids = list(PALETTES.keys())
        if 0 <= index < len(ids):
            self._palette_id = ids[index]
            self._apply_theme()
            palette_name = PALETTES[self._palette_id]["name"]
            mode = "暗色" if self._dark_theme else "亮色"
            self.statusBar().showMessage(f"已切换到 {palette_name} · {mode} 模式")

    # ---------- UI 构建 ----------

    def _setup_ui(self):
        central = QWidget()
        central.setObjectName("centralWidget")  # 配合 QSS 选择器，应用带色调的对角渐变背景
        self.setCentralWidget(central)
        main_layout = QHBoxLayout(central)
        main_layout.setContentsMargins(8, 8, 8, 8)
        main_layout.setSpacing(8)

        splitter = QSplitter(Qt.Horizontal)

        # 左侧：样式列表
        left_panel = QWidget()
        left_layout = QVBoxLayout(left_panel)
        left_layout.setContentsMargins(0, 0, 0, 0)
        left_layout.setSpacing(6)

        list_header = QLabel("样式列表")
        list_header.setObjectName("listHeader")
        list_header.setStyleSheet("font-weight: bold; font-size: 14px; padding: 4px;")
        left_layout.addWidget(list_header)

        btn_layout = QHBoxLayout()
        self.btn_add = QPushButton("➕ 新增")
        self.btn_add.setObjectName("btnAdd")
        self.btn_add.clicked.connect(self._add_style)
        btn_layout.addWidget(self.btn_add)

        self.btn_edit = QPushButton("✏️ 编辑")
        self.btn_edit.setObjectName("btnEdit")
        self.btn_edit.clicked.connect(self._edit_style)
        btn_layout.addWidget(self.btn_edit)

        self.btn_clone = QPushButton("📋 复制")
        self.btn_clone.clicked.connect(self._clone_style)
        btn_layout.addWidget(self.btn_clone)

        self.btn_delete = QPushButton("🗑️ 删除")
        self.btn_delete.setObjectName("btnDelete")
        self.btn_delete.clicked.connect(self._delete_style)
        btn_layout.addWidget(self.btn_delete)

        self.btn_reset = QPushButton("↺ 恢复初始")
        self.btn_reset.setObjectName("btnReset")
        self.btn_reset.setToolTip("将所有样式和设置恢复为内置默认值")
        self.btn_reset.clicked.connect(self._reset_to_initial)
        btn_layout.addWidget(self.btn_reset)

        btn_layout.addStretch()
        left_layout.addLayout(btn_layout)

        self.style_list = QListWidget()
        self.style_list.currentRowChanged.connect(self._on_select_style)
        left_layout.addWidget(self.style_list, 1)

        splitter.addWidget(left_panel)

        # 右侧：预览/编辑区
        right_panel = QWidget()
        right_layout = QVBoxLayout(right_panel)
        right_layout.setContentsMargins(0, 0, 0, 0)
        right_layout.setSpacing(6)

        self.tabs = QTabWidget()

        # Tab 1: 资源包设置
        pack_tab = QWidget()
        pack_layout = QVBoxLayout(pack_tab)
        pack_form = QFormLayout()

        self.pack_desc_edit = QLineEdit(self.pack_description)
        pack_form.addRow("资源包描述:", self.pack_desc_edit)

        self.pack_format_spin = QSpinBox()
        self.pack_format_spin.setRange(1, 99)
        self.pack_format_spin.setValue(self.pack_format)
        self.pack_format_spin.setToolTip("Minecraft 版本对应 pack_format，1.20.1 = 15")
        pack_form.addRow("Pack Format:", self.pack_format_spin)

        self.pack_preview = QTextEdit()
        self.pack_preview.setReadOnly(True)
        self.pack_preview.setStyleSheet("font-family: Consolas, monospace; font-size: 12px;")
        pack_form.addRow("生成预览:", self.pack_preview)

        pack_layout.addLayout(pack_form)
        pack_layout.addStretch()
        self.tabs.addTab(pack_tab, "📦 资源包设置")

        # Tab 2: styles.json 预览/编辑
        json_tab = QWidget()
        json_layout = QVBoxLayout(json_tab)
        json_btn_layout = QHBoxLayout()
        json_btn_label = QLabel("styles.json")
        json_btn_label.setStyleSheet("font-weight: bold;")
        json_btn_label.setObjectName("previewLabel")
        json_btn_layout.addWidget(json_btn_label)
        json_btn_layout.addStretch()

        btn_sync_from_json = QPushButton("📥 从 JSON 同步")
        btn_sync_from_json.setToolTip("解析 JSON 编辑内容并同步到列表")
        btn_sync_from_json.clicked.connect(self._sync_from_json)
        json_btn_layout.addWidget(btn_sync_from_json)

        btn_format_json = QPushButton("✨ 格式化")
        btn_format_json.setToolTip("格式化 JSON 缩进")
        btn_format_json.clicked.connect(self._format_json)
        json_btn_layout.addWidget(btn_format_json)

        btn_reload_json = QPushButton("🔄 重新生成")
        btn_reload_json.setToolTip("根据当前样式列表重新生成 JSON")
        btn_reload_json.clicked.connect(self._update_preview)
        json_btn_layout.addWidget(btn_reload_json)

        json_layout.addLayout(json_btn_layout)

        self.json_preview = QPlainTextEdit()
        self.json_preview.setFont(QFont("Consolas", 10))
        self.json_preview.setPlaceholderText('{"styles": [...]}')
        json_layout.addWidget(self.json_preview)
        self.tabs.addTab(json_tab, "📄 styles.json")

        # Tab 3: CSS 模板
        css_tab = QWidget()
        css_layout = QVBoxLayout(css_tab)
        self.css_editor = QPlainTextEdit()
        self.css_editor.setFont(QFont("Consolas", 10))
        self.css_editor.setPlaceholderText("/* 在此输入你的自定义 CSS */\n.cal-day { ... }")
        css_layout.addWidget(self.css_editor)

        css_btn_layout = QHBoxLayout()
        css_btn_layout.addWidget(QLabel("CSS 文件路径:"))
        self.css_path_edit = QLineEdit("styles/my_style.css")
        self.css_path_edit.setPlaceholderText("styles/my_style.css")
        css_btn_layout.addWidget(self.css_path_edit, 1)

        # 修复：套用模板按钮使用独立的 btnTemplate ID，解决亮色主题下白字白背景
        btn_template = QPushButton("🎨 套用模板")
        btn_template.setObjectName("btnTemplate")
        btn_template.setToolTip("一键套用内置 CSS 模板（3 种风格可选）")
        btn_template.clicked.connect(self._apply_css_template)
        css_btn_layout.addWidget(btn_template)
        css_layout.addLayout(css_btn_layout)

        self.tabs.addTab(css_tab, "🎨 CSS 模板")

        # Tab 4: 帮助
        help_tab = QWidget()
        help_layout = QVBoxLayout(help_tab)
        help_text = QTextEdit()
        help_text.setReadOnly(True)
        help_text.setHtml(self._build_help_html())
        help_layout.addWidget(help_text)
        self.tabs.addTab(help_tab, "❓ 帮助")

        right_layout.addWidget(self.tabs)

        splitter.addWidget(right_panel)
        splitter.setSizes([350, 750])
        main_layout.addWidget(splitter)

    def _build_help_html(self) -> str:
        return """
        <h2 style="color:#1E293B;">Calendar Mod 资源包生成器</h2>
        <h3 style="color:#334155;">快速上手</h3>
        <ol>
            <li>点击 <b>➕ 新增</b> 创建自定义样式</li>
            <li>填写样式 ID、名称、CSS 文件路径</li>
            <li>选择 HUD 颜色（可在颜色选择器中调整 alpha 透明度）</li>
            <li>在 CSS 模板页编写自定义 CSS</li>
            <li>点击 <b>💾 导出资源包</b> 生成 zip 文件</li>
        </ol>

        <h3 style="color:#334155;">字段说明</h3>
        <table border="1" cellpadding="6" style="border-collapse:collapse;">
            <tr><td><b>字段</b></td><td><b>说明</b></td></tr>
            <tr><td>id</td><td>样式唯一标识，英文小写+下划线</td></tr>
            <tr><td>name</td><td>显示名称</td></tr>
            <tr><td>file</td><td>CSS 文件相对 templates/ 的路径</td></tr>
            <tr><td>builtin</td><td>是否内置（资源包样式设为 false）</td></tr>
            <tr><td>hud</td><td>HUD 7 种颜色配置</td></tr>
        </table>

        <h3 style="color:#334155;">HUD 颜色字段</h3>
        <table border="1" cellpadding="6" style="border-collapse:collapse;">
            <tr><td><b>字段</b></td><td><b>用途</b></td></tr>
            <tr><td>shadow</td><td>HUD 外围阴影（多层外扩）</td></tr>
            <tr><td>body</td><td>HUD 主体背景</td></tr>
            <tr><td>decor</td><td>顶部 3px 装饰条</td></tr>
            <tr><td>border</td><td>细边框颜色</td></tr>
            <tr><td>textPrimary</td><td>第 1 行主文字</td></tr>
            <tr><td>textSecondary</td><td>次要文字</td></tr>
            <tr><td>textEvent</td><td>◆ 事件行文字</td></tr>
        </table>

        <h3 style="color:#334155;">颜色格式</h3>
        <p>支持 <code>#RGB</code>、<code>#RGBA</code>、<code>#RRGGBB</code>、<code>#RRGGBBAA</code>。</p>
        <p>alpha 通道在末尾：00=透明，FF=不透明。</p>

        <h3 style="color:#334155;">工具主题</h3>
        <p>工具栏支持 6 种色调：<b>靛蓝、海洋蓝、森林绿、幻境紫、日落橙、樱花粉</b>，每种色调都可以切换 亮 / 暗 模式，共 12 种组合可选。</p>

        <h3 style="color:#334155;">导出的资源包结构</h3>
        <pre>
my_pack.zip
├── pack.mcmeta
└── assets/
    └── calendarmod/
        └── templates/
            ├── styles.json
            └── styles/
                └── my_style.css
        </pre>
        """

    def _setup_toolbar(self):
        toolbar = QToolBar("主工具栏")
        toolbar.setMovable(False)
        self.addToolBar(toolbar)

        act_new = QAction("🆕 新建", self)
        act_new.triggered.connect(self._new_pack)
        toolbar.addAction(act_new)

        act_load = QAction("📂 打开", self)
        act_load.triggered.connect(self._load_pack)
        toolbar.addAction(act_load)

        act_save = QAction("💾 保存配置", self)
        act_save.triggered.connect(self._save_pack)
        toolbar.addAction(act_save)

        act_reset = QAction("↺ 恢复初始", self)
        act_reset.triggered.connect(self._reset_to_initial)
        toolbar.addAction(act_reset)

        toolbar.addSeparator()

        act_export = QAction("📦 导出资源包", self)
        act_export.triggered.connect(self._export_pack)
        toolbar.addAction(act_export)

        act_export_zip = QAction("🗜️ 导出 ZIP", self)
        act_export_zip.triggered.connect(self._export_zip)
        toolbar.addAction(act_export_zip)

        toolbar.addSeparator()

        # 新增：色调下拉选择（6 种主色调）
        palette_label = QLabel(" 🎨 色调:")
        palette_label.setStyleSheet("color: inherit; padding: 0 2px;")
        toolbar.addWidget(palette_label)

        self.palette_combo = QComboBox()
        for pid, pdata in PALETTES.items():
            self.palette_combo.addItem(pdata["name"], pid)
        self.palette_combo.setToolTip("选择工具主题主色调（6 种可选）")
        self.palette_combo.currentIndexChanged.connect(self._on_palette_changed)
        toolbar.addWidget(self.palette_combo)

        toolbar.addSeparator()

        # 亮/暗模式切换按钮
        act_theme = QAction("🌙 暗色模式", self)
        act_theme.triggered.connect(self._toggle_theme)
        self._theme_action = act_theme
        toolbar.addAction(act_theme)

        act_help = QAction("❓ 帮助", self)
        act_help.triggered.connect(lambda: self.tabs.setCurrentIndex(3))
        toolbar.addAction(act_help)

    def _setup_statusbar(self):
        self.statusBar().showMessage("就绪 - 请添加或编辑样式")

    # ----- 数据操作 -----

    def _load_builtin_styles(self):
        """重置为内置 6 个样式，并为每个样式填入对应风格的 CSS 模板。"""
        self.styles.clear()
        for sid, data in BUILTIN_STYLES.items():
            self.styles.append(StyleEntry(
                id_=sid,
                name=data["name"],
                description=data["description"],
                file=data["file"],
                builtin=True,
                hud=data["hud"],
                css=builtin_css_for(sid),   # 关键：每个内置样式自带独立的 CSS 模板
            ))
        self._refresh_list()
        # 默认选中第 0 个（灰白默认），同步到 css_editor
        if self.styles:
            self._current_index = 0
            if hasattr(self, "style_list") and self.style_list is not None:
                self.style_list.blockSignals(True)
                self.style_list.setCurrentRow(0)
                self.style_list.blockSignals(False)
            self._sync_entry_to_editor(self._current_index)

    def _reset_to_initial(self):
        """恢复所有内容为初始默认状态。

        用户明确要求：点恢复初始时，要把"套用模板"的 CSS 代码从编辑器里清除，
        并把 JSON 编辑框、Pack 描述、色调、亮暗模式全部复位。
        """
        confirm = dlg_confirm(
            self, "恢复初始",
            "将恢复所有内容为初始默认状态：\n\n"
            "• 样式列表 → 恢复为 6 个内置样式\n"
            "• CSS 编辑器 / CSS 路径框 → 清空 / 复位\n"
            "• JSON 预览框 → 重新生成\n"
            "• 资源包描述 / Pack Format → 恢复默认\n"
            "• 工具色调 / 亮暗模式 → 恢复靛蓝·亮色\n\n"
            "此操作不可撤销，是否继续？",
            yes_text="恢复(&R)", no_text="取消(&C)", default_no=True
        )
        if not confirm:
            return

        # 1. 复位工具主题（经典灰白·亮色），下拉框也要同步到第 0 项
        self._palette_id = "slate"
        self._dark_theme = False
        if hasattr(self, 'palette_combo') and self.palette_combo is not None:
            self.palette_combo.blockSignals(True)
            self.palette_combo.setCurrentIndex(0)
            self.palette_combo.blockSignals(False)
        if hasattr(self, '_theme_action') and self._theme_action is not None:
            self._theme_action.setText("🌙 暗色模式")

        # 2. 恢复内置样式
        self._load_builtin_styles()

        # 3. 恢复资源包设置（pack_format + 描述）
        self.pack_description = "我的日历自定义样式"
        self.pack_desc_edit.setText(self.pack_description)
        self.pack_format = 15
        self.pack_format_spin.setValue(self.pack_format)

        # 4. 清空 CSS 编辑器 & 复位路径框（核心：用户说"要把模板的东西从编辑器上取消"）
        self.css_editor.blockSignals(True)
        self.css_editor.clear()
        self.css_editor.blockSignals(False)
        self.css_path_edit.setText("styles/my_style.css")

        # 5. 样式列表选中清空 + 切到第 1 个 Tab 给用户明确视觉反馈
        self._current_index = -1
        self.style_list.clearSelection()
        self.tabs.setCurrentIndex(0)
        self.style_list.setFocus()

        # 6. 重新生成 styles.json 预览 + pack.mcmeta 预览
        self._update_preview()

        # 7. 刷新主题界面（主题切到靛蓝·亮色了）
        self._apply_theme()

        self.statusBar().showMessage("已恢复初始状态：6内置样式 + CSS空 + 靛蓝亮色")
        dlg_info(self, "恢复成功",
                 "已恢复为初始默认状态：\n\n"
                 "• CSS 编辑器内容已清空\n"
                 "• 样式列表恢复为 6 个内置\n"
                 "• 工具主题切换为 靛蓝·亮色\n"
                 "• 所有编辑区已复位")

    def _save_editor_to_entry(self, row=None):
        """把 CSS 编辑器（css_editor + css_path_edit）的当前内容写回到指定行的 entry。

        用户任何时刻在 CSS 模板 Tab 中的改动都会保存到「当前选中样式」上，
        避免「改了半天然后切样式，内容丢失」的经典坑。
        """
        if row is None:
            row = self._current_index
        if row is None or row < 0 or row >= len(self.styles):
            return
        if not (hasattr(self, "css_editor") and hasattr(self, "css_path_edit")):
            return
        entry = self.styles[row]
        # blockSignals：防止写入过程中触发 textChanged 再触发一次保存（死循环/重复写）
        self.css_editor.blockSignals(True)
        self.css_path_edit.blockSignals(True)
        try:
            entry.css = self.css_editor.toPlainText()
            entry.file = self.css_path_edit.text().strip() or entry.file or "styles/my_style.css"
        finally:
            self.css_editor.blockSignals(False)
            self.css_path_edit.blockSignals(False)

    def _sync_entry_to_editor(self, row):
        """把指定行 entry 的 CSS 与 file 路径同步到右侧 CSS 编辑器。"""
        if row is None or row < 0 or row >= len(self.styles):
            # 未选中任何样式：清空编辑器占位，不写回 entry
            if hasattr(self, "css_editor"):
                self.css_editor.blockSignals(True)
                self.css_editor.clear()
                self.css_editor.setPlaceholderText(
                    "/* 请先在左侧列表选择一个样式，然后在此编辑它的 CSS */\n"
                    "/* 选中内置样式时会自动载入对应的风格模板 */"
                )
                self.css_editor.blockSignals(False)
            if hasattr(self, "css_path_edit"):
                self.css_path_edit.blockSignals(True)
                self.css_path_edit.setText("styles/my_style.css")
                self.css_path_edit.blockSignals(False)
            return
        entry = self.styles[row]
        self.css_editor.blockSignals(True)
        self.css_path_edit.blockSignals(True)
        try:
            self.css_editor.setPlainText(entry.css or "")
            self.css_path_edit.setText(entry.file or "styles/my_style.css")
        finally:
            self.css_editor.blockSignals(False)
            self.css_path_edit.blockSignals(False)

    def _refresh_list(self):
        self.style_list.clear()
        for s in self.styles:
            prefix = "【内置】" if s.builtin else "【自定义】"
            item = QListWidgetItem(f"{prefix} {s.name} ({s.id})")
            if s.builtin:
                item.setForeground(QColor("#64748B"))
            self.style_list.addItem(item)

    def _on_select_style(self, row: int):
        """切换样式：先保存旧行的编辑 -> 切换 _current_index -> 载入新行的 CSS -> 刷新预览"""
        # 1) 先把当前正在编辑的 CSS 写回到之前选中的 entry 上
        self._save_editor_to_entry(self._current_index)
        # 2) 切换
        self._current_index = row if 0 <= row < len(self.styles) else -1
        # 3) 把新选中 entry 的 CSS 和 file 同步到右侧编辑器
        self._sync_entry_to_editor(self._current_index)
        # 4) 刷新 JSON 预览（包含 CSS 字段）
        self._update_preview()

    def _update_preview(self):
        # 先把编辑器里的内容写回当前 entry，保证预览反映的是最新编辑状态
        self._save_editor_to_entry(self._current_index)

        # 构造「简洁可读」的预览数据：css_b64 只保留前 60 字符 + 长度，让用户一眼看到"有无 CSS 模板"
        # 真正完整的 base64 数据在保存/导出时才使用
        preview_styles = []
        for s in self.styles:
            d = {
                "id": s.id,
                "name": s.name or s.id,
                "description": s.description,
                "file": s.file,
                "builtin": s.builtin,
            }
            if s.hud:
                d["hud"] = dict(s.hud)
            # css 字段：只展示摘要（前 60 字符 + 总长度），避免 base64 长串撑爆预览框
            if s.css:
                css_len = len(s.css)
                snippet = s.css.strip().replace("\n", " ")[:60]
                d["css_info"] = f"[css_长度={css_len}字节] {snippet}..."
            else:
                d["css_info"] = "[空]"
            preview_styles.append(d)

        data = {"styles": preview_styles}
        json_str = json.dumps(data, ensure_ascii=False, indent=2)

        self.pack_preview.setPlainText(
            f"pack.mcmeta 预览:\n"
            f"  pack_format: {self.pack_format_spin.value()}\n"
            f"  description: {self.pack_desc_edit.text()}\n\n"
            f"styles.json 预览（css_info 为编辑器摘要，保存/导出时会写入完整 base64）:\n{json_str}"
        )
        self.json_preview.setPlainText(json_str)

    def _sync_from_json(self):
        """解析 JSON 编辑器内容，同步回样式列表。

        注意：右侧预览框里显示的是 css_info（摘要，不是真 base64），
        如果用户在预览框里手动编辑后点「从 JSON 同步」，会把摘要当成真实 css_b64
        导致解码失败。这里做一层防护：发现条目既没有 css_b64 也没有 css，
        但有 css_info 时，自动回退为「保留原有 CSS」。
        """
        text = self.json_preview.toPlainText().strip()
        if not text:
            dlg_warn(self, "同步失败", "JSON 内容为空")
            return
        try:
            data = json.loads(text)
        except json.JSONDecodeError as e:
            dlg_error(self, "JSON 解析失败", f"JSON 格式错误:\n{e}")
            return

        if "styles" not in data or not isinstance(data["styles"], list):
            dlg_warn(self, "同步失败", "JSON 必须包含 'styles' 数组")
            return

        # 先把当前编辑器保存回 entry（便于后续保留原 CSS）
        self._save_editor_to_entry(self._current_index)
        old_by_id = {s.id: s for s in self.styles}

        styles = []
        for item in data["styles"]:
            try:
                entry = StyleEntry.from_dict(item)
                # 只保留元数据 / hud / file 等字段：若 JSON 里没带 css_b64/css，
                # 但我们原来已经有同 id 的 entry，就把原有的 css 保留下来
                has_real_css = ("css_b64" in item) or ("css" in item and item.get("css"))
                if not has_real_css and entry.id in old_by_id:
                    entry.css = old_by_id[entry.id].css
                styles.append(entry)
            except Exception as e:
                dlg_warn(self, "同步警告", f"解析样式条目失败:\n{item}\n\n错误: {e}")
                return

        confirm = dlg_confirm(
            self, "从 JSON 同步",
            f"将用 JSON 中的 {len(styles)} 个样式替换当前列表，是否继续？\n"
            f"（若 JSON 里仅显示 css_info 摘要，对应样式会保留原有 CSS）",
            yes_text="同步(&S)", no_text="取消(&C)"
        )
        if not confirm:
            return

        self.styles = styles
        self._current_index = -1
        self._refresh_list()
        # 重新选中第 0 项并同步 CSS 编辑器
        if self.styles:
            self._current_index = 0
            if hasattr(self, "style_list"):
                self.style_list.blockSignals(True)
                self.style_list.setCurrentRow(0)
                self.style_list.blockSignals(False)
            self._sync_entry_to_editor(0)
        self._update_preview()
        self.statusBar().showMessage(f"已从 JSON 同步 {len(styles)} 个样式")

    def _format_json(self):
        """格式化 JSON 编辑器内容"""
        text = self.json_preview.toPlainText().strip()
        if not text:
            return
        try:
            data = json.loads(text)
            self.json_preview.setPlainText(json.dumps(data, ensure_ascii=False, indent=2))
            self.statusBar().showMessage("JSON 已格式化")
        except json.JSONDecodeError as e:
            dlg_error(self, "格式化失败", f"JSON 格式错误:\n{e}")

    def _add_style(self):
        dlg = StyleEditDialog(parent=self)
        if dlg.exec():
            style = dlg.get_style()
            # 新增的自定义样式也附带一份「简约白底」模板 CSS（不让用户从空白从零写）
            if not style.css:
                style.css = template_simple_white()
            # 没有填 file 路径时给一个安全默认
            if not style.file:
                style.file = f"styles/{style.id or 'my_style'}.css"
            self.styles.append(style)
            self._refresh_list()
            new_row = len(self.styles) - 1
            self.style_list.setCurrentRow(new_row)
            self.statusBar().showMessage(f"已添加样式: {style.name}")

    def _edit_style(self):
        row = self._current_index
        if row < 0 or row >= len(self.styles):
            dlg_info(self, "提示", "请先选择一个样式")
            return
        # 打开编辑对话框之前：先把 css_editor 当前改动写回，避免对话框修改丢失
        self._save_editor_to_entry(row)
        dlg = StyleEditDialog(self.styles[row], self)
        if dlg.exec():
            new_style = dlg.get_style()
            # 关键：编辑对话框返回的新对象会丢掉原有 CSS（因为对话框没编辑 CSS），所以必须保留！
            new_style.css = self.styles[row].css
            self.styles[row] = new_style
            self._refresh_list()
            self.style_list.setCurrentRow(row)
            # 把同步过的 entry.css 再次显示到编辑器（防止 dialog 改了 file 没同步）
            self._sync_entry_to_editor(row)
            self.statusBar().showMessage(f"已更新样式: {self.styles[row].name}")

    def _clone_style(self):
        row = self._current_index
        if row < 0 or row >= len(self.styles):
            dlg_info(self, "提示", "请先选择一个样式")
            return
        orig = self.styles[row]
        # 先保存当前编辑，保证 clone 的是最新状态
        self._save_editor_to_entry(row)
        new_id = f"{orig.id}_copy"
        counter = 1
        while any(s.id == new_id for s in self.styles):
            new_id = f"{orig.id}_copy{counter}"
            counter += 1
        cloned = StyleEntry(
            id_=new_id,
            name=f"{orig.name} 副本",
            description=orig.description,
            file=orig.file,          # 注意：克隆后 file 与原样式相同；导出时会被覆盖相同路径，用户如要分离请手动改 file 路径
            builtin=False,
            hud=dict(orig.hud),
            css=str(orig.css or ""),  # 关键：别丢了原样式的 CSS 内容！
        )
        self.styles.append(cloned)
        self._refresh_list()
        self.statusBar().showMessage(f"已复制样式: {cloned.name}")

    def _delete_style(self):
        row = self._current_index
        if row < 0 or row >= len(self.styles):
            dlg_info(self, "提示", "请先选择一个样式")
            return
        s = self.styles[row]
        if s.builtin:
            confirm = dlg_confirm(
                self, "确认删除",
                f"确定删除内置样式 '{s.name}'？\n\n删除后将在导出时覆盖该内置样式。",
                yes_text="删除(&D)", no_text="取消(&C)"
            )
        else:
            confirm = dlg_confirm(
                self, "确认删除", f"确定删除样式 '{s.name}'？",
                yes_text="删除(&D)", no_text="取消(&C)"
            )
        if confirm:
            self.styles.pop(row)
            self._refresh_list()
            self._current_index = -1
            self.statusBar().showMessage(f"已删除样式: {s.name}")

    def _new_pack(self):
        confirm = dlg_confirm(
            self, "新建资源包",
            "新建将清空当前所有样式，是否继续？",
            yes_text="新建(&N)", no_text="取消(&C)"
        )
        if confirm:
            self.styles.clear()
            self._refresh_list()
            self.statusBar().showMessage("已新建空资源包")

    def _load_pack(self):
        path, _ = QFileDialog.getOpenFileName(
            self, "打开 styles.json", str(Path.home()),
            "JSON 文件 (*.json)"
        )
        if not path:
            return
        try:
            with open(path, "r", encoding="utf-8") as f:
                data = json.load(f)
            self.styles.clear()
            for item in data.get("styles", []):
                self.styles.append(StyleEntry.from_dict(item))
            self.pack_description = os.path.basename(os.path.dirname(path))
            self.pack_desc_edit.setText(self.pack_description)
            self._refresh_list()
            self._update_preview()
            self.statusBar().showMessage(f"已加载: {path}")
        except Exception as e:
            dlg_error(self, "错误", f"加载失败: {e}")

    def _save_pack(self):
        # 先同步 JSON 编辑区的修改
        text = self.json_preview.toPlainText().strip()
        if text:
            try:
                data = json.loads(text)
                if "styles" in data and isinstance(data["styles"], list):
                    self.styles = [StyleEntry.from_dict(item) for item in data["styles"]]
                    self._refresh_list()
            except json.JSONDecodeError:
                pass  # JSON 无效时忽略，使用当前列表数据

        path, _ = QFileDialog.getSaveFileName(
            self, "保存 styles.json", str(Path.home()),
            "JSON 文件 (*.json)"
        )
        if not path:
            return
        try:
            data = {"styles": [s.to_dict() for s in self.styles]}
            with open(path, "w", encoding="utf-8") as f:
                json.dump(data, f, ensure_ascii=False, indent=2)
            self.statusBar().showMessage(f"已保存: {path}")
        except Exception as e:
            dlg_error(self, "错误", f"保存失败: {e}")

    def _apply_css_template(self):
        """给「当前选中的样式」套用一套 3 选 1 的风格模板。

        之前的 Bug：直接把模板写入全局单例 css_editor，导致所有样式共享同一份 CSS，
        导出时也只生成单一的 my_style.css。
        正确做法：模板作用于当前选中 entry 的 entry.css 字段，然后同步到编辑器显示。
        """
        # 0. 先把 css_editor 当前正在编辑的内容保存到旧 entry，避免用户之前的改动被覆盖
        self._save_editor_to_entry(self._current_index)

        # 1. 必须先选中一个样式，否则提示用户
        if self._current_index is None or self._current_index < 0 or self._current_index >= len(self.styles):
            dlg_info(self, "请先选择样式",
                     "套用模板前，请先在左侧「样式列表」中选择一个目标样式，\n"
                     "这样模板会应用到该样式独立的 CSS 文件中。")
            # 切回第 1 个 Tab，让用户聚焦样式列表
            if hasattr(self, "tabs"):
                self.tabs.setCurrentIndex(0)
            return

        templates_map = {
            "简约白底":  template_simple_white,
            "暗色深夜":  template_dark_night,
            "活力渐变":  template_vibrant_gradient,
        }
        items = list(templates_map.keys())
        # 2. 中文下拉框（替代 QInputDialog.getItem 英文 OK/Cancel 黑底）
        item, ok = dlg_ask_item(
            self, "套用 CSS 模板", "选择要套用的模板风格:",
            items, current=0, editable=False
        )
        if not (ok and item in templates_map):
            return

        # 3. 把模板写入当前选中 entry 的 entry.css 字段
        template_fn = templates_map[item]
        target_entry = self.styles[self._current_index]
        target_entry.css = template_fn()

        # 4. 同步到编辑器显示，并切到 CSS 模板 Tab 给用户立刻看到结果
        self._sync_entry_to_editor(self._current_index)
        self._update_preview()
        self.tabs.setCurrentIndex(2)
        self.css_editor.setFocus()
        self.statusBar().showMessage(
            f"样式「{target_entry.name}」已套用模板: {item}（文件: {target_entry.file}）"
        )

    def _export_pack(self):
        path = QFileDialog.getExistingDirectory(self, "选择导出目录")
        if not path:
            return
        try:
            # 中文文本输入框（替代 QInputDialog.getText 英文 OK/Cancel 黑底）
            name, ok = dlg_ask_text(
                self, "资源包名称", "输入资源包文件夹名:",
                default="my_calendar_style"
            )
            if not ok or not name.strip():
                return
            name = name.strip()
            pack_dir = Path(path) / name
            if pack_dir.exists():
                confirm = dlg_confirm(
                    self, "目录冲突",
                    f"目录 {name} 已存在，是否覆盖原有内容？",
                    yes_text="覆盖(&O)", no_text="取消(&C)", default_no=True
                )
                if not confirm:
                    return
                shutil.rmtree(pack_dir)

            self._do_export(pack_dir)
            dlg_info(self, "导出成功",
                     f"资源包文件夹已生成:\n{pack_dir}\n\n"
                     f"将此文件夹直接放入 .minecraft/resourcepacks/ 即可使用。")
            self.statusBar().showMessage(f"已导出: {pack_dir}")
        except Exception as e:
            dlg_error(self, "错误", f"导出失败: {e}")

    def _export_zip(self):
        save_path, _ = QFileDialog.getSaveFileName(
            self, "导出 ZIP", str(Path.home() / "my_calendar_style.zip"),
            "ZIP 文件 (*.zip)"
        )
        if not save_path:
            return
        try:
            temp_dir = Path.home() / f".calpack_temp_{datetime.now().strftime('%H%M%S')}"
            self._do_export(temp_dir)
            with zipfile.ZipFile(save_path, "w", zipfile.ZIP_DEFLATED) as zf:
                for f in temp_dir.rglob("*"):
                    if f.is_file():
                        zf.write(f, f.relative_to(temp_dir))
            shutil.rmtree(temp_dir)
            dlg_info(self, "导出成功",
                     f"ZIP 资源包已生成:\n{save_path}\n\n"
                     f"直接放入 .minecraft/resourcepacks/ 目录即可（无需解压）。")
            self.statusBar().showMessage(f"已导出 ZIP: {save_path}")
        except Exception as e:
            dlg_error(self, "错误", f"导出失败: {e}")

    def _do_export(self, pack_dir: Path):
        """导出资源包：每个样式独立写自己的 entry.css → entry.file 路径。

        之前 Bug：所有 6 个样式共用一份全局 css_editor，导出时只写一个 my_style.css，
        导致 dark/ocean/forest/mystic/minimal/default 对应文件要么不存在要么内容不对。
        修复：导出前先保存当前编辑；然后遍历 self.styles，每个 entry 写自己的 CSS。
        """
        # 0. 先把用户当前正在 css_editor 里编辑的内容保存回「当前选中样式」，避免最后一次修改丢失
        self._save_editor_to_entry(self._current_index)

        # 1. 先同步 JSON 编辑区的修改（如果用户手动改了 styles.json 预览）
        text = self.json_preview.toPlainText().strip()
        if text:
            try:
                data = json.loads(text)
                if "styles" in data and isinstance(data["styles"], list):
                    self.styles = [StyleEntry.from_dict(item) for item in data["styles"]]
                    self._refresh_list()
            except json.JSONDecodeError:
                pass  # JSON 无效时忽略，使用当前列表数据

        # 2. 创建目录结构
        templates_dir = pack_dir / "assets" / "calendarmod" / "templates"
        styles_dir = templates_dir / "styles"
        styles_dir.mkdir(parents=True, exist_ok=True)

        # 3. 生成 pack.mcmeta
        mcmeta = {
            "pack": {
                "pack_format": self.pack_format_spin.value(),
                "description": self.pack_desc_edit.text(),
            }
        }
        with open(pack_dir / "pack.mcmeta", "w", encoding="utf-8") as f:
            json.dump(mcmeta, f, ensure_ascii=False, indent=2)

        # 4. 生成 styles.json
        data = {"styles": [s.to_dict() for s in self.styles]}
        with open(templates_dir / "styles.json", "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)

        # 5. 生成每个样式独立的 CSS 文件（一个 entry 写一个文件）
        for entry in self.styles:
            # 优先 entry.css；再回退到全局 css_editor（兜底避免空）
            css_content = (entry.css or "").strip() or self.css_editor.toPlainText().strip()
            if not css_content:
                continue
            # 取 entry.file 作为相对路径；空的话给安全兜底（避免 styles.json 里 file 缺失导致不写 CSS）
            rel_path = (entry.file or "").strip()
            if not rel_path:
                safe_id = entry.id or f"style_{self.styles.index(entry)}"
                rel_path = f"styles/{safe_id}.css"
            # 阻止路径穿越攻击（比如 "../../mods/evil.jar"），简单规范：只保留 basename 前的 styles/ 前缀
            clean_rel = rel_path.replace("\\", "/").lstrip("/")
            if ".." in clean_rel.split("/"):
                clean_rel = f"styles/{Path(clean_rel).name}"
            css_full_path = templates_dir / clean_rel
            css_full_path.parent.mkdir(parents=True, exist_ok=True)
            with open(css_full_path, "w", encoding="utf-8") as f:
                f.write(css_content)

        self._update_preview()


# ============================================================
# 入口
# ============================================================

def main():
    app = QApplication(sys.argv)
    app.setStyle("Fusion")

    # 启动时默认使用 经典灰白·亮色 主题（工具风格默认：中性灰阶，无色干扰）
    app.setStyleSheet(build_theme("slate", False))

    window = ResourcePackGenerator()
    window.show()
    sys.exit(app.exec())


if __name__ == "__main__":
    main()
