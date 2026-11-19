# -*- coding: utf-8 -*-
# Генератор ui/hud_screen.json для ресурспака GeyserEvent (v1.18.0).
# Техника: ofunny (https://ofunny.world/, github.com/ofunny/ofunnysBedrockExamples), GPL-3.0.
#
# Формат строки, которую плагин шлёт в action bar Bedrock-игрокам:
#     <МАРКЕР>;<СТАТУС>;<ВРЕМЯ>;<КООРДИНАТЫ>
# например:  #GE_R#;3500/3500~~~~~~~~~~~~~;19:55;714 73 -243
#
# Почему именно так (все три пункта — выстраданные, ломать нельзя):
#
#  1) ВСЕ ДАННЫЕ ОТ ПЛАГИНА — ТОЛЬКО ASCII (цифры, '/', пробел, минус).
#     Кириллица и цветные коды живут в самом паке статическими строками.
#     Причина: непонятно, считает ли '%.Ns' байты или символы. Если бы в поле
#     была кириллица, эти два режима резали бы строку в разных местах и панель
#     ломалась бы то так, то эдак. На ASCII байты == символы, и схема работает
#     гарантированно в любом режиме.
#
#  2) Bedrock подставляет $переменные в свойство контрола НЕглубоко: цепочка
#     «text -> $a -> $b -> $c ...» обрывается, и метка показывает сырой текст
#     выражения («месиво из цифр»: ($rest - $coords - '|' - '~')).
#     Поэтому переменные плоские: их определения используют только
#     $actionbar_text и строковые литералы, без других переменных.
#
#  3) Символ '\n' в action bar Bedrock не переносится — клиент обрезает текст,
#     поэтому строки панели пак рисует сам, а поля разделяет ';'.
#
#  4) Поля СТАТУС и ВРЕМЯ имеют фиксированную ширину: плагин дополняет их '~'
#     до точной ширины. Пак отрезает каждое поле одним '%.Ns' — без переборов
#     длины и без цепочек переменных. '~' он же и вычитает из статуса.
#
#  5) Панель и метки видимы только пока в action bar есть маркер '#GE_';
#     ванильный action bar в это время спрятан (ему добавлен ТОЛЬКО visible —
#     полное переопределение контрола ломает его).
import json, io, os

# --- маркеры начала action bar: по одному на каждый ивент ---
MARKERS = {
    "rift": "#GE_R#",           # Адский Разлом
    "excavation": "#GE_E#",     # Пляжные Раскопки
    "cauldron": "#GE_C#",       # Котёл Алхимика
    "summon": "#GE_S#",         # Призыв босса (ожидание перед появлением)
}
PREFIX = "#GE_"
SEP = ";"                       # разделитель полей
PAD = "~"                       # заполнитель до фиксированной ширины поля

# Маркер всегда 6 символов + ';' = 7. Отрезаем его одним '%.7s' — так выражение
# остаётся коротким (важно: он вставляется в текст каждой метки по несколько раз).
MARKER_LEN = len(next(iter(MARKERS.values()))) + 1

STATUS_BYTES = 24              # фиксированная ширина поля статуса
TIME_BYTES = 5                 # фиксированная ширина поля времени (ММ:СС)

S = "\u00a7"                    # § — код цвета/формата Bedrock
LINE2_SEP = "  " + S + "7\u00b7" + S + "r  "     # "  §7·§r  " — просторный разделитель

# Названия ивентов (пак рисует их сам — по одному label на каждый ивент).
NAMES = {
    "rift": S + "c\u0410\u0414\u0421\u041a\u0418\u0419 \u0420\u0410\u0417\u041b\u041e\u041c",          # АДСКИЙ РАЗЛОМ
    "excavation": S + "e\u041f\u041b\u042f\u0416\u041d\u042b\u0415 \u0420\u0410\u0421\u041a\u041e\u041f\u041a\u0418",  # ПЛЯЖНЫЕ РАСКОПКИ
    "cauldron": S + "d\u041a\u041e\u0422\u0401\u041b \u0410\u041b\u0425\u0418\u041c\u0418\u041a\u0410",              # КОТЁЛ АЛХИМИКА
    "summon": S + "6\u041f\u0420\u0418\u0417\u042b\u0412 \u0411\u041e\u0421\u0421\u0410",                              # ПРИЗЫВ БОССА
}

# Вторая строка панели для каждого ивента. {status} и {time} заменяются на
# выражения, вырезающие соответствующее поле из action bar.
LINE2 = {
    # "❤ 3500/3500  ·  19:55"
    "rift": "('" + S + "c❤ ' + {status} + '" + LINE2_SEP + "' + '" + S + "f' + {time})",
    # "Кисточка: Готова  ·  04:31"  (плагин шлёт 0, когда кисточка готова)
    "excavation": "('" + S + "eКисточка: " + S
                  + "a' + (({status} = '0') ? 'Готова' : ({status} + 'с'))"
                  + " + '" + LINE2_SEP + "' + '" + S + "f' + {time})",
    # "Реагенты: 5  ·  02:00"
    "cauldron": "('" + S + "dРеагенты: " + S
                + "a' + {status} + '" + LINE2_SEP + "' + '" + S + "f' + {time})",
    # "Босс появится через  ·  01:59"  (статус не нужен — текст статичный)
    "summon": "('" + S + "eБосс появится через"
               + "' + '" + LINE2_SEP + "' + '" + S + "f' + {time})",
}

COORDS_LINE = "('" + S + "7Координаты: " + S + "f' + {coords})"


# ------------------------------------------------------------------ молang
def tail():
    """molang: текст action bar без маркера («статус;время;координаты»).

    Маркер всегда занимает MARKER_LEN байт в начале, поэтому отрезаем его одним
    '%.Ns'. Использует только $actionbar_text — больше никаких переменных.
    """
    return "($actionbar_text - ('%." + str(MARKER_LEN) + "s' * $actionbar_text))"


def status_field():
    """molang: поле статуса (фиксированной ширины) без заполнителя '~'."""
    return "(('%." + str(STATUS_BYTES) + "s' * " + tail() + ") - '" + PAD + "')"


def after_status():
    """molang: «время;координаты» — то, что осталось после поля статуса."""
    return "(" + tail() + " - ('%." + str(STATUS_BYTES) + "s' * " + tail() + ") - '" + SEP + "')"


def time_field():
    """molang: поле времени (ММ:СС)."""
    return "('%." + str(TIME_BYTES) + "s' * " + after_status() + ")"


def coords_field():
    """molang: координаты — всё, что после поля времени."""
    return "(" + after_status() + " - ('%." + str(TIME_BYTES) + "s' * " + after_status() + ") - '" + SEP + "')"


def has_marker(marker):
    """true, если текст action bar начинается с этого маркера ивента."""
    return "(($actionbar_text - '" + marker + "') != $actionbar_text)"


def any_marker(invert=False):
    """true, если в тексте action bar есть хоть один наш маркер.

    invert=True — наоборот (маркера НЕТ): для ванильного action bar.
    """
    op = "=" if invert else "!="
    return "(($actionbar_text - '" + PREFIX + "') " + op + " $actionbar_text)"


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
    controls.append(label("geyserevent_name_" + key, NAMES[key], has_marker(marker)))

# --- строка 2: состояние и время (свой label на ивент — у каждого свои слова) ---
for key, marker in MARKERS.items():
    text = (LINE2[key]
            .replace("{status}", status_field())
            .replace("{time}", time_field()))
    controls.append(label("geyserevent_status_" + key, text, has_marker(marker)))

# --- строка 3: координаты (общая для всех ивентов) ---
controls.append(label("geyserevent_coords",
                      COORDS_LINE.replace("{coords}", coords_field()),
                      any_marker()))

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
    "offset": [-6, 6],
    # Панель видна только пока плагин шлёт маркер ивента.
    "visible": any_marker(),
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
    ]
  },

  # Фабрика action bar: вместо ванильной строки создаётся наша панель.
  "geyserevent_hud_text_factory": {
    "type": "panel",
    "factory": {
      "name": "hud_actionbar_text_factory",
      "control_ids": {
        "hud_actionbar_text": "geyserevent_hud_text@hud.geyserevent_hud_text"
      }
    }
  },

  # ---- ванильная строка над хотбаром ----
  # ВАЖНО: только visible и больше ничего. Полное переопределение контрола
  # (type/size/texture/alpha/controls) ломает его — ванильный action bar
  # продолжает показываться вместе с сырым payload поверх нашей панели.
  # Маркера нет в тексте -> ванильная строка работает как обычно.
  "hud_actionbar_text": {
    "visible": any_marker(invert=True)
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
    "// Плагин шлёт action bar вида: #GE_R#;3500/3500~~~~~~~~~~~~~;19:55;714 73 -243\n"
    "//   (маркер ; статус " + str(STATUS_BYTES) + " байт, добит '~' ; время ММ:СС ; координаты).\n"
    "// Данные от плагина — только ASCII: '%.Ns' у Bedrock считается неизвестно в\n"
    "// байтах или символах, а на ASCII это одно и то же. Кириллица и цвета — в паке.\n"
    "// Переменные плоские (только $actionbar_text + литералы): Bedrock подставляет\n"
    "// $переменные в свойство контрола неглубоко, и цепочки рисуются сырым текстом.\n"
    "// Пока маркер есть, пак прячет ванильный action bar (только visible) и рисует\n"
    "//   карточку ивента на ванильном фоне textures/ui/hud_tip_text_background:\n"
    "//   строка 1 — название ивента, строка 2 — состояние с временем, строка 3 — координаты.\n"
    "// Тот же 'namespace': 'hud', что и у ванильного hud_screen.json — иначе фабрика\n"
    "// hud_actionbar_text_factory и переменная $actionbar_text не находятся.\n"
)

body = json.dumps(doc, ensure_ascii=False, indent=2)
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "resourcepack-vanilla", "ui", "hud_screen.json")
os.makedirs(os.path.dirname(OUT), exist_ok=True)
io.open(OUT, "w", encoding="utf-8").write(header + body + "\n")
print("written", OUT)
