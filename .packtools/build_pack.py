# -*- coding: utf-8 -*-
"""Сборка GeyserEvent-Pack.mcpack: ванильный HUD-патч (панель ивента в углу экрана)
+ блоки призыва босса (кастомные текстуры блоков)."""
import io, json, zipfile, os

SRC = "/home/user/GeyserEvent/GeyserEvent-Pack.mcpack"
DST = "/home/user/GeyserEvent/GeyserEvent-Pack.mcpack"
EMBED = "/home/user/GeyserEvent/src/main/resources/resourcepack/GeyserEvent-Pack.mcpack"
VERSION = [1, 16, 0]

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

zin = zipfile.ZipFile(SRC, "r")
entries = {n: zin.read(n) for n in zin.namelist()}
zin.close()

# --- убираем из пака старые текстуры-иконки (иконок нет с v1.15.0) ---
for name in [n for n in entries if n.startswith("textures/geyserevent/")]:
    del entries[name]
    print("dropped:", name)

# --- новый manifest: версия 1.16.0, чтобы Bedrock-клиенты перекачали пак ---
manifest = json.loads(entries["manifest.json"].decode("utf-8"))
manifest["header"]["name"] = "GeyserEvent Pack v1.16.0"
manifest["header"]["description"] = ("3D-Рюкзак шахтёра + панель активного ивента в правом верхнем углу экрана: "
                                    "три строки (название, состояние с временем, координаты) на ванильном фоне; "
                                    "блоки призыва босса (алтарь + 4 пилона) с своими текстурами; "
                                    "ванильный action bar спрячан, пока идёт ивент")
manifest["header"]["version"] = VERSION
manifest["modules"][0]["version"] = VERSION
entries["manifest.json"] = json.dumps(manifest, ensure_ascii=False, indent=2).encode("utf-8")

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
print("hud markers:", [m for m in ("#GE_R#", "#GE_E#", "#GE_C#") if m in hudjson])
