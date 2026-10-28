# -*- coding: utf-8 -*-
# Генератор ui/hud_screen.json для ресурспака GeyserEvent (v1.12.0).
# Техника: ofunny (https://ofunny.world/, github.com/ofunny/ofunnysBedrockExamples), GPL-3.0.
import json, io, os

# --- маркеры начала action bar: по одному на каждый ивент ---
MARKERS = {
    "rift": "#GE_R#",           # Адский Разлом
    "excavation": "#GE_E#",     # Пляжные Раскопки
    "cauldron": "#GE_C#",       # Котёл Алхимика
}

def has(marker, var="$localtext"):
    return "((" + var + " - '" + marker + "') != " + var + ")"

ANY_MARKER = " || ".join(has(m) for m in MARKERS.values())

doc = {
  # Тот же namespace, что и у ванильного hud_screen.json — иначе фабрика
  # hud_actionbar_text_factory и переменная $actionbar_text не находятся.
  "namespace": "hud",

  # ---- панель активного ивента в правом верхнем углу ----
  # Фабрика даёт доступ к $actionbar_text; смещение/привязку задаём явно,
  # иначе панель наследует ванильное смещение и рисуется в центре экрана.
  "geyserevent_hud_text": {
    "type": "panel",
    "size": ["100%c", "100%c"],
    "layer": 31,
    "alpha": 1.0,
    "anchor_from": "top_right",
    "anchor_to": "top_right",
    "offset": [0, 0],
    "controls": [
      {
        "geyserevent_hud_row": {
          "type": "stack_panel",
          "orientation": "horizontal",
          "size": ["100%c", "100%c"],
          "layer": 31,
          "alpha": 1.0,
          "anchor_from": "top_right",
          "anchor_to": "top_right",
          "offset": [0, 4],
          "bindings": [
            {"binding_name": "#show_survival_ui", "binding_name_override": "#visible"}
          ],
          "controls": [
            {
              # --- иконка ивента (рисует ресурспак, не текст) ---
              "geyserevent_hud_icon": {
                "type": "panel",
                "size": [18, 12],
                "layer": 31,
                "controls": [
                  {
                    "geyserevent_icon_rift": {
                      "type": "image",
                      "texture": "textures/geyserevent/icon_rift",
                      "size": [12, 12],
                      "layer": 31,
                      "alpha": 1.0,
                      "anchor_from": "top_left",
                      "anchor_to": "top_left",
                      "offset": [0, 1],
                      "visible": has(MARKERS["rift"])
                    }
                  },
                  {
                    "geyserevent_icon_excavation": {
                      "type": "image",
                      "texture": "textures/geyserevent/icon_excavation",
                      "size": [12, 12],
                      "layer": 31,
                      "alpha": 1.0,
                      "anchor_from": "top_left",
                      "anchor_to": "top_left",
                      "offset": [0, 1],
                      "visible": has(MARKERS["excavation"])
                    }
                  },
                  {
                    "geyserevent_icon_cauldron": {
                      "type": "image",
                      "texture": "textures/geyserevent/icon_cauldron",
                      "size": [12, 12],
                      "layer": 31,
                      "alpha": 1.0,
                      "anchor_from": "top_left",
                      "anchor_to": "top_left",
                      "offset": [0, 1],
                      "visible": has(MARKERS["cauldron"])
                    }
                  }
                ]
              }
            },
            {
              # --- две строки: (1) ивент + состояние, (2) координаты ---
              "geyserevent_hud_label": {
                "type": "label",
                "text": "$formattedtext",
                "color": "$tool_tip_text",
                "shadow": True,
                "layer": 31,
                "alpha": 1.0,
                "localize": False,
                "size": ["default", "default"],
                "offset": [-4, 0]
              }
            }
          ]
        }
      }
    ],
    "$localtext": "$actionbar_text",
    # Убираем маркер ивента и пробел после него — игрок видит только текст.
    "$formattedtext": "($localtext - '" + MARKERS["rift"] + "' - '" + MARKERS["excavation"]
                      + "' - '" + MARKERS["cauldron"] + "' - ' ')",
    "visible": ANY_MARKER
  },
  "geyserevent_hud_text_factory": {
    "type": "panel",
    "factory": {
      "name": "hud_actionbar_text_factory",
      "control_ids": {"hud_actionbar_text": "geyserevent_hud_text@hud.geyserevent_hud_text"}
    }
  },

  # ---- прячем ванильную строку над хотбаром, пока летит маркер ивента ----
  # Только visible: никаких новых переменных, чтобы не сломать ванильную подпись.
  "hud_actionbar_text": {
    "visible": "!(" + " || ".join(has(m, "$actionbar_text") for m in MARKERS.values()) + ")"
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
  "// #GE_E# excavation, #GE_C# cauldron). The pack hides the default action bar while a\n"
  "// marker is present and draws the event panel in the corner: a pack texture icon\n"
  "// (textures/geyserevent/icon_*.png) plus a two line label (event/status, coordinates).\n"
  "// Same 'namespace': 'hud' as the vanilla hud_screen.json so the factory and $actionbar_text\n"
  "// resolve; the panel sets its own anchor/offset because the vanilla action bar layout\n"
  "// would otherwise move it to the middle of the screen.\n"
)

body = json.dumps(doc, ensure_ascii=False, indent=2)
os.makedirs("/tmp/packbuild/ui", exist_ok=True)
io.open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "build", "ui", "hud_screen.json"), "w", encoding="utf-8").write(header + body + "\n")
print(body)
