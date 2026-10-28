# Инструменты сборки ресурспака GeyserEvent

Исходники пака лежат в `src/main/resources/resourcepack-vanilla/`:

- `ui/hud_screen.json` — HUD-элемент панели ивента в правом верхнем углу экрана (Bedrock);
- `textures/geyserevent/icon_*.png` — иконки ивентов (💀 / ⚓ / ⚗), их рисует пак, а не текст.

Скрипты (Python 3, без сторонних библиотек):

```bash
python3 .packtools/make_icons.py   # перегенерировать PNG-иконки (zlib, без PIL)
python3 .packtools/make_hud.py     # перегенерировать ui/hud_screen.json
python3 .packtools/build_pack.py   # собрать GeyserEvent-Pack.mcpack (+ копия в src/main/resources/resourcepack/)
```

Базовая техника HUD-элемента — ofunny (https://ofunny.world/, github.com/ofunny/ofunnysBedrockExamples), GPL-3.0.
