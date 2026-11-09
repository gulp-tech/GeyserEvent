# -*- coding: utf-8 -*-
"""Сборка GeyserEvent-Pack.mcpack v1.21.0.

Пак для Bedrock-клиентов через Geyser:
  • 3D-рюкзак шахтёра (turtle_helmet attachable)
  • блоки призыва босса и обшивки корабля (редкие ванильные блоки)
  • 3D-модели из аддона al3xandre, ПРИВЯЗАННЫЕ к ванильным сущностям:
      minecraft:vindicator → geometry.alien (босс и малые пришельцы)
      minecraft:ghast      → geometry.nave  (тарелка)
    Вызыватель (evoker) НЕ трогаем — магазины/NPC на нём остаются ванильными.
    На Java-сервере Geyser не умеет спавнить ale:alien / ale:nave, поэтому
    идентификаторы кастомного аддона сюда НЕ попадают.
  • звуки пришельцев (sounds/sound_definitions.json)
HUD клиента не меняется.
"""
import io
import json
import os
import uuid
import zipfile

VERSION = [1, 21, 10]
VERSION_STR = ".".join(str(v) for v in VERSION)

PACK_UUID = str(uuid.uuid5(uuid.NAMESPACE_URL, "https://gulp-tech.github.io/GeyserEvent/pack/" + VERSION_STR))
MODULE_UUID = str(uuid.uuid5(uuid.NAMESPACE_URL, "https://gulp-tech.github.io/GeyserEvent/pack-module/" + VERSION_STR))

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
VANILLA = os.path.join(ROOT, "src", "main", "resources", "resourcepack-vanilla")
DST = os.path.join(ROOT, "GeyserEvent-Pack.mcpack")
EMBED = os.path.join(ROOT, "src", "main", "resources", "resourcepack", "GeyserEvent-Pack.mcpack")

entries = {}


def add_file(arcname, path):
    with open(path, "rb") as fh:
        entries[arcname.replace("\\", "/")] = fh.read()
        print(" +", arcname, len(entries[arcname.replace("\\", "/")]), "bytes")


def add_tree(rel_dir, allowed_ext=None):
    src = os.path.join(VANILLA, rel_dir)
    if not os.path.isdir(src):
        print(" skip missing", rel_dir)
        return
    for root, _, files in os.walk(src):
        for name in sorted(files):
            if "copia" in name.lower() or name.startswith("."):
                continue
            if allowed_ext and not name.lower().endswith(tuple(allowed_ext)):
                continue
            full = os.path.join(root, name)
            rel = os.path.relpath(full, VANILLA).replace(os.sep, "/")
            add_file(rel, full)


# --- manifest ---
manifest = {
    "format_version": 2,
    "header": {
        "name": "GeyserEvent Pack v" + VERSION_STR,
        "description": (
        "3D-рюкзак шахтёра, блоки призыва, 3D-пришелец (vindicator), "
                            "тарелка (гаст), древний сундук ивента. Обычные сундуки не тронуты."
        ),
        "uuid": PACK_UUID,
        "version": VERSION,
        "min_engine_version": [1, 19, 80],
    },
    "modules": [
        {
            "type": "resources",
            "uuid": MODULE_UUID,
            "version": VERSION,
        }
    ],
}
entries["manifest.json"] = json.dumps(manifest, ensure_ascii=False, indent=2).encode("utf-8")
print("pack uuid:", PACK_UUID)

# --- miner backpack + alien/nave models, anims, entities, textures, sounds ---
add_tree("attachables", (".json",))
add_tree("models", (".json",))
add_tree("animations", (".json",))
add_tree("animation_controllers", (".json",))
add_tree("render_controllers", (".json",))
add_tree("entity", (".json",))
add_tree("particles", (".json",))
add_tree("textures", (".png", ".tga", ".json"))
add_tree("sounds", (".wav", ".ogg", ".fsb"))

icon = os.path.join(VANILLA, "pack_icon.png")
if os.path.exists(icon):
    add_file("pack_icon.png", icon)

# --- blocks: summon + alien landing ---
SUMMON_BLOCKS = {
    "reinforced_deepslate": "geyserevent_summon_altar",
    "ochre_froglight": "geyserevent_summon_pylon_1",
    "verdant_froglight": "geyserevent_summon_pylon_2",
    "pearlescent_froglight": "geyserevent_summon_pylon_3",
    "budding_amethyst": "geyserevent_summon_pylon_4",
}
ALIEN_BLOCKS = {
    "dark_prismarine": "geyserevent_alien_hull",
    "purpur_block": "geyserevent_alien_alloy",
    "conduit": "geyserevent_alien_core",
    "end_stone_bricks": "geyserevent_alien_bio",
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
    "texture_data": {
        tex: {"textures": "textures/blocks/geyserevent/" + tex.replace("geyserevent_", "")}
        for tex in ALL_BLOCKS.values()
    },
}, ensure_ascii=False, indent=2).encode("utf-8")

# --- Bedrock sound definitions (Java-style sounds.json from resources is NOT used by Bedrock) ---
sound_defs = {
    "format_version": "1.14.0",
    "sound_definitions": {},
}
for key in ("alien_growl", "alien_screech", "alien_ambient", "alien_transmit", "alien_death"):
    sound_defs["sound_definitions"][key] = {
        "category": "hostile" if key != "alien_ambient" else "ambient",
        "max_distance": 64 if key != "alien_death" else 80,
        "sounds": [{"name": "sounds/alien/" + key, "volume": 1.0}],
    }
entries["sounds/sound_definitions.json"] = json.dumps(sound_defs, ensure_ascii=False, indent=2).encode("utf-8")
print("sound_definitions:", list(sound_defs["sound_definitions"]))

# sanity: vanilla identifiers only in entity files
for name, data in list(entries.items()):
    if name.startswith("entity/") and name.endswith(".json"):
        text = data.decode("utf-8")
        if "ale:" in text:
            raise SystemExit("ОШИБКА: в %s остался идентификатор ale:* — Geyser его не заспавнит" % name)
        if "minecraft:" not in text:
            raise SystemExit("ОШИБКА: в %s нет ванильного идентификатора minecraft:*" % name)

for target in (DST, EMBED):
    os.makedirs(os.path.dirname(target) or ".", exist_ok=True)
    with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as zf:
        for name in sorted(entries):
            zf.writestr(name, entries[name])
    print("written", target, os.path.getsize(target), "bytes")

with zipfile.ZipFile(DST) as z:
    names = z.namelist()
    print("files:", len(names))
    print("entities:", [n for n in names if n.startswith("entity/")])
    print("models:", [n for n in names if n.startswith("models/")])
    print("sounds:", [n for n in names if n.startswith("sounds/")])
    print("hud_screen.json in pack:", "ui/hud_screen.json" in names, "(must be False)")
    print("pack version / uuid:", VERSION, PACK_UUID)
    print("bad zip:", z.testzip())
