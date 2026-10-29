# -*- coding: utf-8 -*-
# Генератор ui/hud_screen.json для ресурспака GeyserEvent (v1.14.0).
# Техника: ofunny (https://ofunny.world/, github.com/ofunny/ofunnysBedrockExamples), GPL-3.0.
import json, io, os

# --- маркеры начала action bar: по одному на каждый ивент ---
# У всех общий префикс "#GE_" — по нему пак понимает «ивент идёт» одной проверкой,
# а полный маркер говорит, какую иконку показать.
MARKERS = {
    "rift": "#GE_R#",           # Адский Разлом
    "excavation": "#GE_E#",     # Пляжные Раскопки
    "cauldron": "#GE_C#",       # Котёл Алхимика
}
PREFIX = "#GE_"

def has(marker, var="$localtext"):
    return "((" + var + " - '" + marker + "') != " + var + ")"

NO_MARKER = "(($actionbar_text - '" + PREFIX + "') = $actionbar_text)"

def icon_control(name, texture, marker):
    return {
        name: {
            "type": "image",
            "texture": "textures/geyserevent/" + texture,
            "size": [12, 12],
            "layer": 32,
            "alpha": 1.0,
            "anchor_from": "top_left",
            "anchor_to": "top_left",
            "offset": [0, 0],
            "visible": has(marker)
        }
    }

doc = {
  # Тот же namespace, что и у ванильного hud_screen.json — иначе фабрика
  # hud_actionbar_text_factory и переменная $actionbar_text не находятся.
  "namespace": "hud",

  # ---- панель активного ивента в правом верхнем углу ----
  # Фон — ванильная «таблетка» textures/ui/hud_tip_text_background, размер считается
  # по содержимому (100%c), поэтому панель всегда ровно по тексту.
  "geyserevent_hud_text": {
    "type": "image",
    "texture": "textures/ui/hud_tip_text_background",
    "size": ["100%c + 16px", "100%c + 10px"],
    "layer": 31,
    "alpha": 1.0,
    "anchor_from": "top_right",
    "anchor_to": "top_right",
    "offset": [-4, 4],
    "controls": [
      # --- иконка ивента: рисует ресурспак (12x12, по одной на каждый ивент) ---
      {
        "geyserevent_icon_box": {
          "type": "panel",
          "size": [12, 12],
          "anchor_from": "top_left",
          "anchor_to": "top_left",
          "offset": [6, 9],
          "controls": [
            icon_control("geyserevent_icon_rift", "icon_rift", MARKERS["rift"]),
            icon_control("geyserevent_icon_excavation", "icon_excavation", MARKERS["excavation"]),
            icon_control("geyserevent_icon_cauldron", "icon_cauldron", MARKERS["cauldron"])
          ]
        }
      },
      # --- две строки: (1) ивент + состояние, (2) координаты ---
      {
        "geyserevent_hud_label": {
          "type": "label",
          "text": "$formattedtext",
          "color": "$tool_tip_text",
          "shadow": True,
          "layer": 32,
          "alpha": 1.0,
          "localize": False,
          "size": ["default", "default"],
          "anchor_from": "top_left",
          "anchor_to": "top_left",
          "offset": [23, 4],
          "bindings": [
            {"binding_name": "#show_survival_ui", "binding_name_override": "#visible"}
          ]
        }
      }
    ],
    "$localtext": "$actionbar_text",
    # Убираем маркер ивента и пробел после него — игрок видит только текст.
    "$formattedtext": "($localtext - '" + MARKERS["rift"] + "' - '" + MARKERS["excavation"]
                      + "' - '" + MARKERS["cauldron"] + "' - ' ')",
    # Общий префикс "#GE_" — одна проверка на любой ивент.
    "visible": has(PREFIX)
  },
  "geyserevent_hud_text_factory": {
    "type": "panel",
    "factory": {
      "name": "hud_actionbar_text_factory",
      "control_ids": {"hud_actionbar_text": "geyserevent_hud_text@hud.geyserevent_hud_text"}
    }
  },

  # ---- ванильная строка над хотбаром: показываем её ТОЛЬКО когда маркера ивента нет ----
  # Полное определение ванильного контрола (фон + подпись), но alpha уходит в 0,
  # пока плагин шлёт маркер ивента — сырой "#GE_E# ..." игрок не видит.
  "hud_actionbar_text": {
    "type": "image",
    "size": ["100%c + 12px", "100%c + 5px"],
    "offset": [0, "50%-68px"],
    "texture": "textures/ui/hud_tip_text_background",
    "alpha": "(" + NO_MARKER + " ? @hud.anim_actionbar_text_background_alpha_out : 0)",
    "controls": [
      {
        "actionbar_message": {
          "type": "label",
          "anchor_from": "center",
          "anchor_to": "center",
          "color": "$tool_tip_text",
          "layer": 31,
          "text": "$actionbar_text",
          "localize": False,
          "alpha": "(" + NO_MARKER + " ? @hud.anim_actionbar_text_alpha_out : 0)"
        }
      }
    ]
  },

  "root_panel": {
    "modifications": [
      {
        "array_name": "controls",
        "operation": "insert_front",
        "value": [
          {"geyserevent_hud_text_factory@hud.geyserevent_hud_text_factory": {}}
        ]
      }
    ]
  }
}

header = (
  "// GeyserEvent: panel of the active event in the top-right corner of the screen (Bedrock).\n"
  "// Base technique and code: ofunny (https://ofunny.world/, github.com/ofunny/ofunnysBedrockExamples), GPL-3.0.\n"
  "// The plugin sends an action bar starting with an event marker (#GE_R# rift,\n"
  "// #GE_E# excavation, #GE_C# cauldron). While a marker is present the pack:\n"
  "//  - hides the vanilla action bar (its alpha is forced to 0), and\n"
  "//  - draws the event card in the top-right corner: pack texture icon\n"
  "//    (textures/geyserevent/icon_*.png) + two line label (event/status, coordinates)\n"
  "//    on the vanilla tip background textures/ui/hud_tip_text_background.\n"
  "// Same 'namespace': 'hud' as the vanilla hud_screen.json so the factory and $actionbar_text\n"
  "// resolve; the panel sets its own anchor/offset because the vanilla action bar layout\n"
  "// would otherwise move it to the middle of the screen.\n"
)

body = json.dumps(doc, ensure_ascii=False, indent=2)
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "resourcepack-vanilla", "ui", "hud_screen.json")
os.makedirs(os.path.dirname(OUT), exist_ok=True)
io.open(OUT, "w", encoding="utf-8").write(header + body + "\n")
print("written", OUT)
