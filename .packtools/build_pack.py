# -*- coding: utf-8 -*-
"""Сборка GeyserEvent-Pack.mcpack: ванильный HUD-патч (панель ивента в углу экрана)."""
import io, json, zipfile, os

SRC = "/home/user/GeyserEvent/GeyserEvent-Pack.mcpack"
DST = "/home/user/GeyserEvent/GeyserEvent-Pack.mcpack"
EMBED = "/home/user/GeyserEvent/src/main/resources/resourcepack/GeyserEvent-Pack.mcpack"
VERSION = [1, 15, 0]

zin = zipfile.ZipFile(SRC, "r")
entries = {n: zin.read(n) for n in zin.namelist()}
zin.close()

# --- убираем из пака старые текстуры-иконки (в v1.15.0 иконок нет) ---
for name in [n for n in entries if n.startswith("textures/geyserevent/")]:
    del entries[name]
    print("dropped:", name)

# --- новый manifest: версия 1.15.0, чтобы Bedrock-клиенты перекачали пак ---
manifest = json.loads(entries["manifest.json"].decode("utf-8"))
manifest["header"]["name"] = "GeyserEvent Pack v1.15.0"
manifest["header"]["description"] = ("3D-Рюкзак шахтёра + панель активного ивента в правом верхнем углу экрана: "
                                    "три строки (название, состояние с временем, координаты) на ванильном фоне; "
                                    "ванильный action bar спрячан, пока идёт ивент")
manifest["header"]["version"] = VERSION
manifest["modules"][0]["version"] = VERSION
entries["manifest.json"] = json.dumps(manifest, ensure_ascii=False, indent=2).encode("utf-8")

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
