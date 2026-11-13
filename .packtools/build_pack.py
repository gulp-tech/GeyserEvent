# -*- coding: utf-8 -*-
"""Сборка GeyserEvent-Pack.mcpack v1.21.0.

Пак для Bedrock-клиентов через Geyser:
  • 3D-рюкзак шахтёра (turtle_helmet attachable)
  • блоки призыва босса и обшивки корабля (редкие ванильные блоки)
  • 3D-модели geyserevent:alien / geyserevent:nave (НЕ minecraft:vindicator/ghast).
  • звуки пришельцев (sounds/sound_definitions.json)
HUD клиента не меняется.
"""
import io
import json
import os
import uuid
import zipfile

VERSION = [1, 21, 29]
VERSION_STR = ".".join(str(v) for v in VERSION)

PACK_UUID = str(uuid.uuid5(uuid.NAMESPACE_URL, "https://gulp-tech.github.io/GeyserEvent/pack/" + VERSION_STR))
MODULE_UUID = str(uuid.uuid5(uuid.NAMESPACE_URL, "https://gulp-tech.github.io/GeyserEvent/pack-module/" + VERSION_STR))

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
VANILLA = os.path.join(ROOT, "src", "main", "resources", "resourcepack-vanilla")
DST = os.path.join(ROOT, "GeyserEvent-Pack.mcpack")
EMBED = os.path.join(ROOT, "src", "main", "resources", "resourcepack", "GeyserEvent-Pack.mcpack")

entries = {}


# Не пакуем оверрайды ванильных сущностей/текстур. Исходники остаются в resourcepack-vanilla.
SKIP_PREFIXES = (
    "textures/entity/chest/",
    "textures/entity/ghast/",
)
SKIP_FILES = {
    "entity/evocation_illager.entity.json",
    "entity/ravager.entity.json",
    "entity/vindicator.entity.json",
    "entity/ghast.entity.json",
    "textures/entity/vindicator.png",
    "models/entity/vindicator.geo.json",
    "models/entity/ghast.geo.json",
    "render_controllers/evoker_ronaldo.json",
    "render_controllers/skibidi_toilet.json",
    "render_controllers/skeleton.json",
    "render_controllers/default.json",
    "render_controllers/vindicator.json",
    "render_controllers/ghast.json",
}


def skip_pack(arcname):
    name = arcname.replace("\\", "/")
    if name in SKIP_FILES:
        return True
    return any(name.startswith(prefix) for prefix in SKIP_PREFIXES)


def add_file(arcname, path):
    name = arcname.replace("\\", "/")
    if skip_pack(name):
        print(" skip vanilla override", name)
        return
    with open(path, "rb") as fh:
        entries[name] = fh.read()
        print(" +", name, len(entries[name]), "bytes")


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
        "description": "3D-рюкзак шахтёра и блоки призыва. Ванильные мобы и эндер-сундук не подменяются.",
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
# ALIEN_BLOCKS не кладём в пак — иначе ванильные призмарин/пурпур/конуит меняют текстуру у всех.
ALL_BLOCKS = dict(SUMMON_BLOCKS)

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
for key, file, dist in (
        ("skibidi_boss", "boss", 80),
        ("skibidi_ambient", "ambient", 48),
        ("skibidi_chase", "chase", 48),
        ("skibidi_death", "death", 64),
):
    sound_defs["sound_definitions"][key] = {
        "category": "hostile",
        "max_distance": dist,
        "sounds": [{"name": "sounds/skibidi/" + file, "volume": 1.0}],
    }
sound_defs["sound_definitions"]["ronaldo_siuu"] = {
    "category": "hostile",
    "max_distance": 64,
    "sounds": [{"name": "sounds/ronaldo/siuu", "volume": 1.6}],
}
sound_defs["sound_definitions"]["ronaldo_talk"] = {
    "category": "hostile",
    "max_distance": 64,
    "sounds": [{"name": "sounds/ronaldo/talk", "volume": 1.4}],
}
entries["sounds/sound_definitions.json"] = json.dumps(sound_defs, ensure_ascii=False, indent=2).encode("utf-8")
print("sound_definitions:", list(sound_defs["sound_definitions"]))

# vindicator/ghast/evoker не трогаем. husk — только ивент пришельцев (в джунглях не спавнится).
ALLOWED_ENTITY = {
    "entity/geyserevent_alien.entity.json",
    "entity/geyserevent_nave.entity.json",
    "entity/husk.entity.json",
}
for name in list(entries):
    if name.startswith("entity/") and name not in ALLOWED_ENTITY:
        raise SystemExit("ОШИБКА: в паке entity/ ломает ванильных мобов: " + name)
    if name.startswith("textures/entity/chest/") or name.startswith("textures/entity/ghast/"):
        raise SystemExit("ОШИБКА: ванильная текстура в паке: " + name)
    if name in SKIP_FILES:
        raise SystemExit("ОШИБКА: запрещённый оверрайд в паке: " + name)

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
