# -*- coding: utf-8 -*-
"""Сборка GeyserEvent-Pack.mcpack: ванильный HUD-патч (панель ивента в углу экрана)
+ блоки призыва босса (кастомные текстуры блоков)."""
import io, json, zipfile, os, uuid

SRC = "/home/user/GeyserEvent/GeyserEvent-Pack.mcpack"
DST = "/home/user/GeyserEvent/GeyserEvent-Pack.mcpack"
EMBED = "/home/user/GeyserEvent/src/main/resources/resourcepack/GeyserEvent-Pack.mcpack"
VERSION = [1, 20, 3]
VERSION_STR = ".".join(str(v) for v in VERSION)

# Bedrock кэширует серверные паки ПО UUID и НЕ перекачивает их заново, если UUID
# совпадает. Поэтому UUID обязан меняться при каждой правке содержимого пака —
# иначе клиенты остаются со старой версией (панель ивента рисуется по-старому,
# а новый маркер #GE_S# превращается в «месиво из цифр»).
# UUID детерминирован от версии: сборка одной и той же версии даёт тот же UUID,
# а новая версия — всегда новый UUID и гарантированную перекачку у всех игроков.
PACK_UUID = str(uuid.uuid5(uuid.NAMESPACE_URL, "https://gulp-tech.github.io/GeyserEvent/pack/" + VERSION_STR))
MODULE_UUID = str(uuid.uuid5(uuid.NAMESPACE_URL, "https://gulp-tech.github.io/GeyserEvent/pack-module/" + VERSION_STR))

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

zin = zipfile.ZipFile(SRC, "r")
entries = {n: zin.read(n) for n in zin.namelist()}
zin.close()

# --- убираем из пака старые текстуры-иконки (иконок нет с v1.15.0) ---
for name in [n for n in entries if n.startswith("textures/geyserevent/")]:
    del entries[name]
    print("dropped:", name)

# --- убираем панель ивента из пака целиком (v1.9.29) ---
# В базовом паке ui/hud_screen.json есть — его надо удалить, иначе клиент
# продолжит рисовать панель и прятать ванильный action bar.
if "ui/hud_screen.json" in entries:
    del entries["ui/hud_screen.json"]
    print("dropped: ui/hud_screen.json (панель ивента убрана по просьбе)")

# --- новый manifest: версия + НОВЫЙ UUID, чтобы Bedrock-клиенты перекачали пак ---
manifest = json.loads(entries["manifest.json"].decode("utf-8"))
manifest["header"]["name"] = "GeyserEvent Pack v" + VERSION_STR
manifest["header"]["description"] = ("3D-Рюкзак шахтёра + блоки призыва босса "
                                    "(алтарь + 4 пилона) + ивент «Вторжение Пришельцев»: "
                                    "кастомные текстуры корабля и пришельцев; "
                                    "HUD клиента не меняется — информация ивента "
                                    "только в BossBar сверху и GPS в action bar")
manifest["header"]["uuid"] = PACK_UUID
manifest["header"]["version"] = VERSION
manifest["modules"][0]["uuid"] = MODULE_UUID
manifest["modules"][0]["version"] = VERSION
entries["manifest.json"] = json.dumps(manifest, ensure_ascii=False, indent=2).encode("utf-8")
print("pack uuid:", PACK_UUID, "(новый на каждую версию — клиенты перекачивают пак)")

# --- блоки призыва босса: текстуры + привязка к ванильным блокам ---
BLOCK_TEX_DIR = os.path.join(ROOT, "src", "main", "resources", "resourcepack-vanilla", "textures", "blocks", "geyserevent")
for name in sorted(os.listdir(BLOCK_TEX_DIR)):
    if name.endswith(".png"):
        entries["textures/blocks/geyserevent/" + name] = io.open(os.path.join(BLOCK_TEX_DIR, name), "rb").read()
        print("block texture:", "textures/blocks/geyserevent/" + name, len(entries["textures/blocks/geyserevent/" + name]), "bytes")

# Какие ванильные блоки получают кастомные текстуры.
# Блоки выбраны так, чтобы не ломать обычные постройки игроков.
SUMMON_BLOCKS = {
    "reinforced_deepslate": "geyserevent_summon_altar",
    "ochre_froglight": "geyserevent_summon_pylon_1",
    "verdant_froglight": "geyserevent_summon_pylon_2",
    "pearlescent_froglight": "geyserevent_summon_pylon_3",
    "budding_amethyst": "geyserevent_summon_pylon_4",
}

# --- ивент «Вторжение Пришельцев»: свои текстуры блоков корабля ---
# Блоки подобраны так, чтобы их не было в схемах других ивентов: переопределение
# текстуры видно ТОЛЬКО на посадочной площадке пришельцев.
ALIEN_BLOCKS = {
    "dark_prismarine": "geyserevent_alien_hull",   # обшивка корабля
    "purpur_block": "geyserevent_alien_alloy",     # сплав пришельцев
    "conduit": "geyserevent_alien_core",           # энерго-ядро
    "end_stone_bricks": "geyserevent_alien_bio",   # био-масса
}

ALL_BLOCKS = dict(SUMMON_BLOCKS)
ALL_BLOCKS.update(ALIEN_BLOCKS)

entries["blocks.json"] = json.dumps({
    "format_version": [1, 1, 0],
    "blocks": {name: {"textures": tex} for name, tex in ALL_BLOCKS.items()},
}, ensure_ascii=False, indent=2).encode("utf-8")

entries["terrain_texture.json"] = json.dumps({
    "resource_pack_name": "geyserevent",
    "texture_name": "atlas.terrain",
    "padding": 8,
    "num_mip_levels": 4,
    "texture_data": {tex: {"textures": "textures/blocks/geyserevent/" + tex.replace("geyserevent_", "")}
                      for tex in ALL_BLOCKS.values()},
}, ensure_ascii=False, indent=2).encode("utf-8")
print("blocks.json / terrain_texture.json:", len(entries["blocks.json"]), "/", len(entries["terrain_texture.json"]), "bytes")

# --- entity-текстуры пришельцев (перекраска ванильных) ---
# Босс-пришелец = EVOKER, малые пришельцы = VEX: оба встречаются в игре редко,
# поэтому перекраска не ломает привычный вид мира.
ENTITY_TEX_DIR = os.path.join(ROOT, "src", "main", "resources", "resourcepack-vanilla", "textures", "entity")
entity_files = []
for root, _, files in os.walk(ENTITY_TEX_DIR):
    for file in sorted(files):
        if file.endswith(".png"):
            full = os.path.join(root, file)
            rel = os.path.relpath(full, ENTITY_TEX_DIR).replace(os.sep, "/")
            entries["textures/entity/" + rel] = io.open(full, "rb").read()
            entity_files.append("textures/entity/" + rel)
print("entity textures:", ", ".join(entity_files))

# --- ui/hud_screen.json В ПАК НЕ ПОПАДАЕТ (v1.9.29) ---
# По просьбе пользователя панель ивента в углу экрана убрана. Пока на экране остаётся
# только BossBar (GPS-локатор), а action bar свободен для GPS.
# Генератор .packtools/make_hud.py сохранён — когда будем делать панель заново,
# вернём сюда вставку entries["ui/hud_screen.json"].
# Без этого файла пак не трогает вообще ничего в HUD клиента.


for target in (DST, EMBED):
    os.makedirs(os.path.dirname(target), exist_ok=True)
    with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as zf:
        for name in sorted(entries):
            zf.writestr(name, entries[name])
    print("written", target, os.path.getsize(target), "bytes")

z = zipfile.ZipFile(DST)
print("files:", z.namelist())
print("bad zip entry check:", z.testzip())
# диагностика врает (новый маркер есть в паке, а проверка его не видит).
print("pack version / uuid:", manifest["header"]["version"], manifest["header"]["uuid"])
print("hud_screen.json в паке:", "ui/hud_screen.json" in z.namelist(), "(должно быть False — панель убрана)")
