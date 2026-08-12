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


class StyleEntry:
    """单个样式条目"""

    def __init__(self, id_="", name="", description="", file="",
                 builtin=False, hud=None):
        self.id = id_
        self.name = name
        self.description = description
        self.file = file
        self.builtin = builtin
        self.hud = hud or {}

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
        return d

    @classmethod
    def from_dict(cls, d: dict) -> "StyleEntry":
        return cls(
            id_=d.get("id", ""),
            name=d.get("name", ""),
            description=d.get("description", ""),
            file=d.get("file", ""),
            builtin=d.get("builtin", False),
            hud=d.get("hud", {}),
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
            btn.setStyleSheet("""
                QPushButton { border: 1px solid #ddd; border-radius: 3px;
                              background: #f8f8f8; padding: 0 8px; }
                QPushButton:hover { background: #e8e8e8; border-color: #999; }
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
        self.preview_label.setAlignment(Qt.AlignCenter)
        self.preview_label.setStyleSheet("font-weight: bold; color: #333;")
        layout.addWidget(self.preview_label)

        self.hud_preview = QFrame()
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
            QMessageBox.warning(self, "输入错误", "样式 ID 不能为空")
            return
        if not file_:
            QMessageBox.warning(self, "输入错误", "CSS 文件路径不能为空")
            return
        if not re.match(r'^[a-z][a-z0-9_]*$', id_):
            QMessageBox.warning(self, "输入错误",
                                "样式 ID 必须以小写字母开头，只含小写字母、数字和下划线")
            return

        self._style = StyleEntry(
            id_=id_,
            name=self.name_edit.text().strip() or id_,
            description=self.desc_edit.text().strip(),
            file_=file_,
            builtin=self.builtin_check.isChecked(),
            hud=self.hud_panel.get_hud(),
        )
        self.accept()

    def get_style(self) -> StyleEntry:
        return self._style


# ============================================================
# 主窗口
# ============================================================

class ResourcePackGenerator(QMainWindow):
    """资源包生成器主窗口"""

    def __init__(self):
        super().__init__()
        self.setWindowTitle("Calendar Mod 资源包生成器")
        self.setMinimumSize(1100, 750)
        self.resize(1200, 800)

        self.styles: list[StyleEntry] = []
        self.pack_format = 15
        self.pack_description = "我的日历自定义样式"
        self._current_index = -1

        self._setup_ui()
        self._setup_toolbar()
        self._setup_statusbar()
        self._load_builtin_styles()

    def _setup_ui(self):
        central = QWidget()
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
        list_header.setStyleSheet("font-weight: bold; font-size: 14px; padding: 4px;")
        left_layout.addWidget(list_header)

        btn_layout = QHBoxLayout()
        self.btn_add = QPushButton("➕ 新增")
        self.btn_add.clicked.connect(self._add_style)
        btn_layout.addWidget(self.btn_add)

        self.btn_edit = QPushButton("✏️ 编辑")
        self.btn_edit.clicked.connect(self._edit_style)
        btn_layout.addWidget(self.btn_edit)

        self.btn_clone = QPushButton("📋 复制")
        self.btn_clone.clicked.connect(self._clone_style)
        btn_layout.addWidget(self.btn_clone)

        self.btn_delete = QPushButton("🗑️ 删除")
        self.btn_delete.clicked.connect(self._delete_style)
        btn_layout.addWidget(self.btn_delete)

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
        self.pack_preview.setMaximumBlockCount(100)
        self.pack_preview.setStyleSheet("font-family: Consolas, monospace; font-size: 12px;")
        pack_form.addRow("生成预览:", self.pack_preview)

        pack_layout.addLayout(pack_form)
        pack_layout.addStretch()
        self.tabs.addTab(pack_tab, "📦 资源包设置")

        # Tab 2: styles.json 预览
        json_tab = QWidget()
        json_layout = QVBoxLayout(json_tab)
        self.json_preview = QPlainTextEdit()
        self.json_preview.setReadOnly(True)
        self.json_preview.setFont(QFont("Consolas", 10))
        self.json_preview.setStyleSheet("""
            QPlainTextEdit {
                background: #1E1E1E;
                color: #D4D4D4;
                border: 1px solid #333;
                border-radius: 4px;
                padding: 8px;
            }
        """)
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

        btn_template = QPushButton("套用模板")
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

        toolbar.addSeparator()

        act_export = QAction("📦 导出资源包", self)
        act_export.triggered.connect(self._export_pack)
        toolbar.addAction(act_export)

        act_export_zip = QAction("🗜️ 导出 ZIP", self)
        act_export_zip.triggered.connect(self._export_zip)
        toolbar.addAction(act_export_zip)

        toolbar.addSeparator()

        act_help = QAction("❓ 帮助", self)
        act_help.triggered.connect(lambda: self.tabs.setCurrentIndex(3))
        toolbar.addAction(act_help)

    def _setup_statusbar(self):
        self.statusBar().showMessage("就绪 - 请添加或编辑样式")

    # ----- 数据操作 -----

    def _load_builtin_styles(self):
        self.styles.clear()
        for sid, data in BUILTIN_STYLES.items():
            self.styles.append(StyleEntry(
                id_=sid,
                name=data["name"],
                description=data["description"],
                file=data["file"],
                builtin=True,
                hud=data["hud"],
            ))
        self._refresh_list()

    def _refresh_list(self):
        self.style_list.clear()
        for s in self.styles:
            prefix = "【内置】" if s.builtin else "【自定义】"
            item = QListWidgetItem(f"{prefix} {s.name} ({s.id})")
            if s.builtin:
                item.setForeground(QColor("#64748B"))
            self.style_list.addItem(item)

    def _on_select_style(self, row: int):
        self._current_index = row
        if 0 <= row < len(self.styles):
            s = self.styles[row]
            self.pack_description = f"我的日历自定义样式"
            self.pack_desc_edit.setText(self.pack_description)
            self._update_preview()

    def _update_preview(self):
        data = {
            "styles": [s.to_dict() for s in self.styles]
        }
        json_str = json.dumps(data, ensure_ascii=False, indent=2)

        self.pack_preview.setPlainText(
            f"pack.mcmeta 预览:\n"
            f"  pack_format: {self.pack_format_spin.value()}\n"
            f"  description: {self.pack_desc_edit.text()}\n\n"
            f"styles.json 预览:\n{json_str}"
        )
        self.json_preview.setPlainText(json_str)

    def _add_style(self):
        dlg = StyleEditDialog(parent=self)
        if dlg.exec():
            style = dlg.get_style()
            self.styles.append(style)
            self._refresh_list()
            self.statusBar().showMessage(f"已添加样式: {style.name}")

    def _edit_style(self):
        row = self._current_index
        if row < 0 or row >= len(self.styles):
            QMessageBox.information(self, "提示", "请先选择一个样式")
            return
        dlg = StyleEditDialog(self.styles[row], self)
        if dlg.exec():
            self.styles[row] = dlg.get_style()
            self._refresh_list()
            self.style_list.setCurrentRow(row)
            self.statusBar().showMessage(f"已更新样式: {self.styles[row].name}")

    def _clone_style(self):
        row = self._current_index
        if row < 0 or row >= len(self.styles):
            QMessageBox.information(self, "提示", "请先选择一个样式")
            return
        orig = self.styles[row]
        new_id = f"{orig.id}_copy"
        counter = 1
        while any(s.id == new_id for s in self.styles):
            new_id = f"{orig.id}_copy{counter}"
            counter += 1
        cloned = StyleEntry(
            id_=new_id,
            name=f"{orig.name} 副本",
            description=orig.description,
            file=orig.file,
            builtin=False,
            hud=dict(orig.hud),
        )
        self.styles.append(cloned)
        self._refresh_list()
        self.statusBar().showMessage(f"已复制样式: {cloned.name}")

    def _delete_style(self):
        row = self._current_index
        if row < 0 or row >= len(self.styles):
            QMessageBox.information(self, "提示", "请先选择一个样式")
            return
        s = self.styles[row]
        if s.builtin:
            ret = QMessageBox.warning(
                self, "确认",
                f"确定删除内置样式 '{s.name}'？\n\n删除后将在导出时覆盖该内置样式。",
                QMessageBox.Yes | QMessageBox.No, QMessageBox.No
            )
        else:
            ret = QMessageBox.question(
                self, "确认", f"确定删除样式 '{s.name}'？",
                QMessageBox.Yes | QMessageBox.No, QMessageBox.No
            )
        if ret == QMessageBox.Yes:
            self.styles.pop(row)
            self._refresh_list()
            self._current_index = -1
            self.statusBar().showMessage(f"已删除样式: {s.name}")

    def _new_pack(self):
        ret = QMessageBox.question(
            self, "新建",
            "新建将清空当前所有样式，是否继续？",
            QMessageBox.Yes | QMessageBox.No, QMessageBox.No
        )
        if ret == QMessageBox.Yes:
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
            QMessageBox.critical(self, "错误", f"加载失败: {e}")

    def _save_pack(self):
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
            QMessageBox.critical(self, "错误", f"保存失败: {e}")

    def _apply_css_template(self):
        templates = {
            "简约白底": self._css_simple,
            "暗色深夜": self._css_dark,
            "活力渐变": self._css_gradient,
        }
        items = list(templates.keys())
        item, ok = QInputDialog.getItem(self, "套用 CSS 模板", "选择模板:",
                                         items, 0, False)
        if ok and item in templates:
            self.css_editor.setPlainText(templates[item]())
            self.statusBar().showMessage(f"已套用模板: {item}")

    def _css_simple(self) -> str:
        return """/* 简约白底模板 */
.cal-root { background: #FFFFFF; color: #1E293B; font-family: system-ui, sans-serif; }
.cal-header { padding: 16px; border-bottom: 1px solid #E2E8F0; }
.cal-title { font-size: 18px; font-weight: bold; }
.cal-subtitle { color: #64748B; font-size: 12px; }
.cal-nav-btn { background: #F1F5F9; border: 1px solid #CBD5E1; border-radius: 6px; }
.cal-nav-btn:hover { background: #E2E8F0; }
.cal-day { display: block; width: calc(100% / 7); text-align: center; padding: 8px; }
.cal-day.today { background: #3B82F620; border-radius: 8px; }
.cal-day.has-event::after { content: ""; display: block; width: 6px; height: 6px; border-radius: 50%; background: currentColor; margin: 2px auto 0; }
.cal-event { padding: 12px; background: #F8FAFC; border-radius: 8px; margin-bottom: 8px; }"""

    def _css_dark(self) -> str:
        return """/* 暗色深夜模板 */
.cal-root { background: #0F172A; color: #E2E8F0; font-family: system-ui, sans-serif; }
.cal-header { padding: 16px; border-bottom: 1px solid #1E293B; }
.cal-title { font-size: 18px; font-weight: bold; color: #F1F5F9; }
.cal-subtitle { color: #94A3B8; font-size: 12px; }
.cal-nav-btn { background: #1E293B; border: 1px solid #334155; border-radius: 6px; color: #E2E8F0; }
.cal-nav-btn:hover { background: #334155; }
.cal-day { display: block; width: calc(100% / 7); text-align: center; padding: 8px; color: #CBD5E1; }
.cal-day.today { background: #3B82F630; border-radius: 8px; color: #F1F5F9; }
.cal-day.future { opacity: 0.4; }
.cal-day.has-event::after { content: ""; display: block; width: 6px; height: 6px; border-radius: 50%; background: #FBBF24; margin: 2px auto 0; }
.cal-event { padding: 12px; background: #1E293B; border-radius: 8px; margin-bottom: 8px; border-left: 3px solid #3B82F6; }"""

    def _css_gradient(self) -> str:
        return """/* 活力渐变模板 */
.cal-root { background: linear-gradient(135deg, #667EEA 0%, #764BA2 100%); color: #FFFFFF; font-family: system-ui, sans-serif; min-height: 100vh; }
.cal-header { padding: 20px; }
.cal-title { font-size: 22px; font-weight: bold; color: #FFFFFF; text-shadow: 0 2px 4px rgba(0,0,0,0.2); }
.cal-subtitle { color: rgba(255,255,255,0.8); font-size: 13px; }
.cal-nav-btn { background: rgba(255,255,255,0.2); border: 1px solid rgba(255,255,255,0.3); border-radius: 20px; color: #FFFFFF; backdrop-filter: blur(10px); }
.cal-nav-btn:hover { background: rgba(255,255,255,0.3); }
.cal-day { display: block; width: calc(100% / 7); text-align: center; padding: 10px; border-radius: 10px; color: rgba(255,255,255,0.9); }
.cal-day.today { background: rgba(255,255,255,0.3); font-weight: bold; box-shadow: 0 4px 12px rgba(0,0,0,0.15); }
.cal-day.has-event::after { content: ""; display: block; width: 6px; height: 6px; border-radius: 50%; background: #FDE047; margin: 2px auto 0; box-shadow: 0 0 8px #FDE047; }
.cal-event { padding: 14px; background: rgba(255,255,255,0.2); border-radius: 12px; margin-bottom: 10px; backdrop-filter: blur(10px); border: 1px solid rgba(255,255,255,0.3); }"""

    def _export_pack(self):
        path = QFileDialog.getExistingDirectory(self, "选择导出目录")
        if not path:
            return
        try:
            name, ok = QInputDialog.getText(
                self, "资源包名称", "输入资源包文件夹名:",
                text="my_calendar_style"
            )
            if not ok or not name.strip():
                return
            name = name.strip()
            pack_dir = Path(path) / name
            if pack_dir.exists():
                ret = QMessageBox.warning(
                    self, "冲突", f"目录 {name} 已存在，是否覆盖？",
                    QMessageBox.Yes | QMessageBox.No, QMessageBox.No
                )
                if ret != QMessageBox.Yes:
                    return
                shutil.rmtree(pack_dir)

            self._do_export(pack_dir)
            QMessageBox.information(self, "成功",
                                    f"资源包已生成:\n{pack_dir}\n\n"
                                    f"将此文件夹放入 .minecraft/resourcepacks/ 即可使用。")
            self.statusBar().showMessage(f"已导出: {pack_dir}")
        except Exception as e:
            QMessageBox.critical(self, "错误", f"导出失败: {e}")

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
            QMessageBox.information(self, "成功",
                                    f"ZIP 已生成:\n{save_path}\n\n"
                                    f"直接放入 .minecraft/resourcepacks/ 解压即可。")
            self.statusBar().showMessage(f"已导出 ZIP: {save_path}")
        except Exception as e:
            QMessageBox.critical(self, "错误", f"导出失败: {e}")

    def _do_export(self, pack_dir: Path):
        # 创建目录结构
        styles_dir = pack_dir / "assets" / "calendarmod" / "templates" / "styles"
        styles_dir.mkdir(parents=True, exist_ok=True)

        # 生成 pack.mcmeta
        mcmeta = {
            "pack": {
                "pack_format": self.pack_format_spin.value(),
                "description": self.pack_desc_edit.text(),
            }
        }
        with open(pack_dir / "pack.mcmeta", "w", encoding="utf-8") as f:
            json.dump(mcmeta, f, ensure_ascii=False, indent=2)

        # 生成 styles.json
        data = {"styles": [s.to_dict() for s in self.styles]}
        with open(pack_dir / "assets" / "calendarmod" / "templates" / "styles.json",
                  "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)

        # 生成 CSS 文件
        css_content = self.css_editor.toPlainText()
        css_path = self.css_path_edit.text().strip()
        if css_content.strip():
            css_full_path = pack_dir / "assets" / "calendarmod" / "templates" / css_path
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

    window = ResourcePackGenerator()
    window.show()
    sys.exit(app.exec())


if __name__ == "__main__":
    main()
