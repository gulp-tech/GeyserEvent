# Инструменты сборки ресурспака GeyserEvent

Исходники пака лежат в `src/main/resources/resourcepack-vanilla/`:

- `ui/hud_screen.json` — HUD-элемент панели ивента в правом верхнем углу экрана (Bedrock):
  три строки (название ивента / состояние с временем / координаты) на ванильном фоне
  `hud_tip_text_background` + скрытие ванильного action bar на время ивента.

Иконок-текстур у панели нет (убраны в v1.15.0 по просьбе пользователя): пак рисует
только текст.

## Блоки призыва босса (v1.16.0)

`textures/blocks/geyserevent/*.png` — пять текстур 16×16 (алтарь + 4 пилона),
всего ~1 КБ. `blocks.json` привязывает их к редким ванильным блокам
(`reinforced_deepslate`, 3 фроглайта, `budding_amethyst`), а `terrain_texture.json` регистрирует их в атласе.
Те же блоки должны быть в `config.yml` → `summon.altar-block` / `summon.pylon-N-block`,
иначе плагин построит алтарь из блоков без текстур.

Скрипты (Python 3, без сторонних библиотек):

```bash
python3 .packtools/make_summon_textures.py  # перегенерировать текстуры блоков призыва (16×16 PNG)
python3 .packtools/make_hud.py              # перегенерировать ui/hud_screen.json
python3 .packtools/build_pack.py            # собрать GeyserEvent-Pack.mcpack (+ копия в src/main/resources/resourcepack/)
python3 .packtools/build_jar.py             # собрать GeyserEvent-1.0.0.jar (ECJ)
```

## Формат данных (плагин → пак)

Плагин раз в секунду присылает одну строку action bar:

```
#GE_R#|19:55~|3500/3500~|100 50 200
```

- `#GE_R#` / `#GE_E#` / `#GE_C#` — маркер ивента (Адский Разлом / Раскопки / Котёл);
- `|` — разделитель полей;
- `~` — «защита»: ставится плагином перед каждым `|` внутри переменной по длине поля,
  чтобы пак не мог спутать поле со следующим (без неё короткое состояние не разрезается,
  а `3500/3500` ломает разбор координат).

Пак извлекает поля выражением `('%.Ns' * $s)` (первые N единиц строки) и собирает
строки панели: `$time`, `$status`, `$coords` → три подписи с `visible` по маркеру ивента.

Базовая техника HUD-элемента — ofunny (https://ofunny.world/, github.com/ofunny/ofunnysBedrockExamples), GPL-3.0.
