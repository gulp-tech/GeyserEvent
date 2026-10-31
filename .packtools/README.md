# Инструменты сборки ресурспака GeyserEvent

Исходники пака лежат в `src/main/resources/resourcepack-vanilla/`:

- `ui/hud_screen.json` — HUD-элемент панели ивента в правом верхнем углу экрана (Bedrock).
  **В v1.9.29 панель убрана по просьбе игрока**: файл есть в репозитории, но в пак **не попадает**
  (`build_pack.py` явно удаляет его из базового ванильного пака), поэтому HUD клиента не меняется вообще.
  В паке остаются только рюкзак шахтёра и 5 текстур блоков призыва (13 файлов).
  Информацию ивента рисует плагин: BossBar сверху + GPS в action bar.

  Если панель будут возвращать: `python3 .packtools/make_hud.py` и вернуть в `build_pack.py`
  добавление `entries["ui/hud_screen.json"]` и снятие удаления файла из базового пака.

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

До v1.9.28 плагин раз в секунду присылал в action bar строку с маркером и полями панели:

```
#GE_R#|19:55~|3500/3500~|100 50 200
```

В v1.9.29 этот контракт удалён вместе со всей панелью: action bar теперь только для GPS-строки
(`InfernalRiftManager`), а маркеры `#GE_R#` / `#GE_E#` / `#GE_C#` / `#GE_S#` в коде не существуют.
При возврате к панели их нужно восстанавливать с нуля.

Базовая техника HUD-элемента — ofunny (https://ofunny.world/, github.com/ofunny/ofunnysBedrockExamples), GPL-3.0.