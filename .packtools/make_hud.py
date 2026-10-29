# -*- coding: utf-8 -*-
# Генератор ui/hud_screen.json для ресурспака GeyserEvent (v1.15.0).
# Техника: ofunny (https://ofunny.world/, github.com/ofunny/ofunnysBedrockExamples), GPL-3.0.
#
# Формат строки, которую плагин шлёт в action bar Bedrock-игрокам:
#     <МАРКЕР>|<ВРЕМЯ>~|<СТАТУС>~|<КООРДИНАТЫ>
# например:  #GE_R#|19:55~|3500/3500~|100 50 200
#
# Почему так:
#  * символ '\n' в action bar Bedrock не переносится на вторую строку — клиент
#    обрезает текст, поэтому строки панели пак рисует сам, а данные разделяет
#    вертикальной чертой '|';
#  * '~' — «страж»: стоит в конце каждого отрезаемого поля и не встречается в
#    тексте больше нигде. Благодаря ему поле не может случайно совпасть со своим
#    собственным началом, поэтому лишний вариант '%.Ns' не сработает;
#  * все отрезаемые поля — ASCII (время и статус: цифры, ':' , '/' или русские
#    буквы), поэтому операция '%.Ns' (первые N символов строки) считает их
#    одинаково и в символах, и в байтах — граница поля находится гарантированно;
#  * позиция второй черты (длина поля времени + 1 + длина поля статуса >= 9)
#    никогда не попадает в диапазон 4..7, которым отрезается время;
#  * координаты — последнее поле, после него ничего отрезать не надо.
import json, io, os

# --- маркеры начала action bar: по одному на каждый ивент ---
MARKERS = {
    "rift": "#GE_R#",           # Адский Разлом
    "excavation": "#GE_E#",     # Пляжные Раскопки
    "cauldron": "#GE_C#",       # Котёл Алхимика
}
PREFIX = "#GE_"

S = "\u00a7"                    # § — код цвета/формата Bedrock
GUARD = "~"                     # «страж» конца поля (в тексте не встречается)
SEP = "  " + S + "7\u00b7" + S + "r  "   # "  §7·§r  " — просторный разделитель

# Диапазоны длин полей вместе со «стражем» (в символах И в байтах одновременно).
TIME_LEN = range(4, 9)          # время ММ:СС + страж (длина 3..8, реально 5)
STATUS_LEN = range(2, 33)       # статус + страж (длина 1..31, реально ≤ 13)

# Названия ивентов (пак рисует их сам — по одному label на ивент).
NAMES = {
    "rift": S + "c\u0410\u0414\u0421\u041a\u0418\u0419 \u0420\u0410\u0417\u041b\u041e\u041c",          # АДСКИЙ РАЗЛОМ
    "excavation": S + "e\u041f\u041b\u042f\u0416\u041d\u042b\u0415 \u0420\u0410\u0421\u041a\u041e\u041f\u041a\u0418",  # ПЛЯЖНЫЕ РАСКОПКИ
    "cauldron": S + "d\u041a\u041e\u0422\u0401\u041b \u0410\u041b\u0425\u0418\u041c\u0418\u041a\u0410",              # КОТЁЛ АЛХИМИКА
}

# Префикс строки состояния для каждого ивента (значение подставляет плагин).
STATUS_PREFIX = {
    "rift": S + "c\u2764 ",                       # "§c❤ "
    "excavation": S + "e\u041a\u0438\u0441\u0442\u043e\u0447\u043a\u0430: " + S + "a",  # "§eКисточка: §a"
    "cauldron": S + "d\u0420\u0435\u0430\u0433\u0435\u043d\u0442\u044b: " + S + "a",   # "§dРеагенты: §a"
}
COORDS_PREFIX = S + "7\u041a\u043e\u043e\u0440\u0434\u0438\u043d\u0430\u0442\u044b: " + S + "f"  # "§7Координаты: §f"


def has(marker, var="$raw"):
    """true, если переменная var содержит маркер ивента."""
    return "((" + var + " - '" + marker + "') != " + var + ")"


def strip_terms(var, lengths):
    """molang: вычесть из var все возможные ('первые N символов' + '|').

    Приём из примеров ofunny: позиция черты в строке неизвестна, поэтому вычитаем
    все варианты префикса заданной длины — лишним остаётся только то, что после черты.
    """
    return "".join(" - ('%." + str(n) + "s' * " + var + " + '|')" for n in lengths)


def label(name, text, visible=None):
    ctrl = {
        "type": "label",
        "text": text,
        "localize": False,
        "shadow": True,
        "layer": 32,
        "color": "$tool_tip_text",
        "text_alignment": "left"
    }
    if visible is not None:
        ctrl["visible"] = visible
    return {name: ctrl}


controls = []
# --- строка 1: название ивента (статичный текст, свой label на каждый ивент) ---
for key, marker in MARKERS.items():
    controls.append(label("geyserevent_name_" + key, NAMES[key], has(marker)))
# --- строка 2: состояние и время ---
for key, marker in MARKERS.items():
    controls.append(label("geyserevent_status_" + key, "$status_" + key, has(marker)))
# --- строка 3: координаты (общая для всех ивентов) ---
controls.append(label("geyserevent_coords", "$coords_line"))

NO_MARKER = "(($actionbar_text - '" + PREFIX + "') = $actionbar_text)"

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
    "size": ["100%c + 18px", "100%c + 12px"],
    "layer": 31,
    "alpha": 1.0,
    "anchor_from": "top_right",
    "anchor_to": "top_right",
    "offset": [-4, 4],
    "controls": [
      {
        "geyserevent_card": {
          "type": "stack_panel",
          "orientation": "vertical",
          "anchor_from": "top_left",
          "anchor_to": "top_left",
          "offset": [9, 6],
          "controls": controls
        }
      }
    ],
    # Исходный текст action bar, который прислала плагин.
    "$raw": "$actionbar_text",
    # Убираем маркер ивента вместе с чертой — остаётся «время~|статус~|координаты».
    "$tail": "($raw - '#GE_R#|' - '#GE_E#|' - '#GE_C#|')",
    # Отрезаем поле времени (4..7 символов вместе со стражем) — остаётся «статус~|координаты».
    "$rest": "($tail" + strip_terms("$tail", TIME_LEN) + ")",
    "$time": "($tail - $rest - '|' - '" + GUARD + "')",
    # Отрезаем поле статуса (2..24 символа вместе со стражем) — остаются только координаты.
    "$coords": "($rest" + strip_terms("$rest", STATUS_LEN) + ")",
    "$status": "($rest - $coords - '|' - '" + GUARD + "')",
    # Готовые строки панели.
    "$status_rift": "('" + STATUS_PREFIX["rift"] + "' + $status + '" + SEP + "' + '" + S + "f' + $time)",
    "$status_excavation": "('" + STATUS_PREFIX["excavation"] + "' + $status + '" + SEP + "' + '" + S + "f' + $time)",
    "$status_cauldron": "('" + STATUS_PREFIX["cauldron"] + "' + $status + '" + SEP + "' + '" + S + "f' + $time)",
    "$coords_line": "('" + COORDS_PREFIX + "' + $coords)",
    # Панель видна только пока плагин шлёт маркер ивента.
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
  "// GeyserEvent: панель активного ивента в правом верхнем углу экрана (Bedrock).\n"
  "// Базовая техника и код: ofunny (https://ofunny.world/, github.com/ofunny/ofunnysBedrockExamples), GPL-3.0.\n"
  "// Плагин шлёт action bar вида: #GE_R#|19:55~|3500/3500~|100 50 200\n"
  "//   (маркер ивента | время ММ:СС | статус | координаты; '~' — страж конца поля).\n"
  "// Пока маркер есть, пак:\n"
  "//   - прячет ванильный action bar (его alpha принудительно 0), и\n"
  "//   - рисует карточку ивента на ванильном фоне textures/ui/hud_tip_text_background:\n"
  "//     строка 1 — название ивента, строка 2 — статус и время, строка 3 — координаты.\n"
  "// Разделение строк делает сам пак (моланг не умеет переносить '\\n' из action bar):\n"
  "// поля ASCII, поэтому операция '%.Ns' срабатывает одинаково и в символах, и в байтах,\n"
  "// а '~' не даёт полсу совпасть со своим собственным началом.\n"
  "// Тот же 'namespace': 'hud', что и у ванильного hud_screen.json — иначе фабрика\n"
  "// hud_actionbar_text_factory и переменная $actionbar_text не находятся.\n"
)

body = json.dumps(doc, ensure_ascii=False, indent=2)
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "resourcepack-vanilla", "ui", "hud_screen.json")
os.makedirs(os.path.dirname(OUT), exist_ok=True)
io.open(OUT, "w", encoding="utf-8").write(header + body + "\n")
print("written", OUT)
