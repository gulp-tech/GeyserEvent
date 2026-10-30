# -*- coding: utf-8 -*-
"""Сборка GeyserEvent-Pack.mcpack: ванильный HUD-патч (панель ивента в углу экрана)
+ блоки призыва босса (кастомные текстуры блоков)."""
import io, json, zipfile, os, uuid

SRC = "/home/user/GeyserEvent/GeyserEvent-Pack.mcpack"
DST = "/home/user/GeyserEvent/GeyserEvent-Pack.mcpack"
EMBED = "/home/user/GeyserEvent/src/main/resources/resourcepack/GeyserEvent-Pack.mcpack"
VERSION = [1, 17, 0]
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

# --- новый manifest: версия + НОВЫЙ UUID, чтобы Bedrock-клиенты перекачали пак ---
manifest = json.loads(entries["manifest.json"].decode("utf-8"))
manifest["header"]["name"] = "GeyserEvent Pack v" + VERSION_STR
manifest["header"]["description"] = ("3D-Рюкзак шахтёра + панель активного ивента в правом верхнем углу экрана: "
                                    "три строки (название, состояние с временем, координаты) на ванильном фоне; "
                                    "блоки призыва босса (алтарь + 4 пилона) с своими текстурами; "
                                    "ванильный action bar спрячан, пока идёт ивент")
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

entries["blocks.json"] = json.dumps({
    "format_version": [1, 1, 0],
    "blocks": {name: {"textures": tex} for name, tex in SUMMON_BLOCKS.items()},
}, ensure_ascii=False, indent=2).encode("utf-8")

entries["terrain_texture.json"] = json.dumps({
    "resource_pack_name": "geyserevent",
    "texture_name": "atlas.terrain",
    "padding": 8,
    "num_mip_levels": 4,
    "texture_data": {tex: {"textures": "textures/blocks/geyserevent/" + tex.replace("geyserevent_", "")}
                      for tex in SUMMON_BLOCKS.values()},
}, ensure_ascii=False, indent=2).encode("utf-8")
print("blocks.json / terrain_texture.json:", len(entries["blocks.json"]), "/", len(entries["terrain_texture.json"]), "bytes")

# --- новый ui/hud_screen.json ---
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
HUD = os.path.join(ROOT, "src", "main", "resources", "resourcepack-vanilla", "ui", "hud_screen.json")
hud = io.open(HUD, encoding="utf-8").read()
entries["ui/hud_screen.json"] = hud.encode("utf-8")

for target in (DST, EMBED):
    os.makedirs(os.path.dirname(target), exist_ok=True)
    with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as zf:
        for name in sorted(entries):
            zf.writestr(name, entries[name])
    print("written", target, os.path.getsize(target), "bytes")

z = zipfile.ZipFile(DST)
print("files:", z.namelist())
print("bad zip entry check:", z.testzip())
hudjson = z.read("ui/hud_screen.json").decode("utf-8")
# маркеры читаем из самого hud_screen.json, а не из списка в коде — иначе
# диагностика врает (новый маркер есть в паке, а проверка его не видит).
import re as _re
print("hud markers in pack:", sorted(set(_re.findall(r"#GE_[A-Z]#", hudjson))))
print("pack version / uuid:", manifest["header"]["version"], manifest["header"]["uuid"])
