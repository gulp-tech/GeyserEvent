#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Цитадель Адского Разлома 25×25 — сплошной пол, врата, 4 башни."""
from __future__ import annotations
import math
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "schematics", "infernal_citadel_25.schem.yml")

PALETTE = {
    "B": "blackstone",
    "P": "polished_blackstone_bricks",
    "C": "cracked_polished_blackstone_bricks",
    "H": "chiseled_polished_blackstone",
    "G": "gilded_blackstone",
    "M": "magma_block",
    "O": "obsidian",
    "Y": "crying_obsidian",
    "L": "lodestone",
    "W": "polished_blackstone_brick_wall",
    "F": "nether_brick_fence",
    "N": "netherrack",
    "S": "soul_fire",
    "X": "nether_portal",
    "T": "soul_lantern",
    "R": "crimson_nylium",
    "D": "polished_blackstone_brick_slab",
    "A": "respawn_anchor",
}

RADIUS = 12
SIZE = 25
C = 12
TOWERS = [(8, 8), (8, -8), (-8, 8), (-8, -8)]
# Врата на севере (z отрицательный), чтобы центр оставался проходимым
GATE_Z = -9


def empty():
    return [["." for _ in range(SIZE)] for _ in range(SIZE)]


def setb(g, x, z, s):
    gx, gz = C + x, C + z
    if 0 <= gx < SIZE and 0 <= gz < SIZE:
        g[gz][gx] = s


def disk(g, r, s):
    for x in range(-RADIUS, RADIUS + 1):
        for z in range(-RADIUS, RADIUS + 1):
            if math.hypot(x, z) <= r + 0.2:
                setb(g, x, z, s)


def ring(g, r0, r1, s):
    for x in range(-RADIUS, RADIUS + 1):
        for z in range(-RADIUS, RADIUS + 1):
            d = math.hypot(x, z)
            if r0 - 0.45 <= d <= r1 + 0.45:
                setb(g, x, z, s)


def dump(g):
    return "\n".join('    - "' + "".join(row) + '"' for row in g)


def layer0():
    g = empty()
    disk(g, 11.3, "B")
    ring(g, 10.0, 11.0, "O")
    ring(g, 8.0, 9.2, "P")
    ring(g, 6.0, 7.2, "M")
    ring(g, 4.0, 5.2, "C")
    disk(g, 3.2, "P")
    setb(g, 0, 0, "L")
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        setb(g, dx, dz, "G")
    for x, z in TOWERS:
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                setb(g, x + dx, z + dz, "P")
        setb(g, x, z, "Y")
    # основание врат
    for x in range(-3, 4):
        setb(g, x, GATE_Z, "O")
        setb(g, x, GATE_Z + 1, "Y")
    return g


def layer1():
    g = empty()
    for x, z in TOWERS:
        setb(g, x, z, "P")
    for x in range(-3, 4):
        setb(g, x, GATE_Z, "O")
    setb(g, -3, GATE_Z, "P")
    setb(g, 3, GATE_Z, "P")
    setb(g, 0, GATE_Z, "H")
    # 4 прохода в ободе
    for x, z in ((0, 11), (0, -11), (11, 0), (-11, 0)):
        setb(g, x, z, "D")
    return g


def layer2():
    g = empty()
    for x, z in TOWERS:
        setb(g, x, z, "Y")
    setb(g, -3, GATE_Z, "O")
    setb(g, 3, GATE_Z, "O")
    for x in (-2, -1, 0, 1, 2):
        setb(g, x, GATE_Z, "X")
    setb(g, -4, GATE_Z, "W")
    setb(g, 4, GATE_Z, "W")
    return g


def layer3():
    g = empty()
    for x, z in TOWERS:
        setb(g, x, z, "W")
    setb(g, -3, GATE_Z, "Y")
    setb(g, 3, GATE_Z, "Y")
    for x in (-2, -1, 0, 1, 2):
        setb(g, x, GATE_Z, "X")
    setb(g, -4, GATE_Z, "F")
    setb(g, 4, GATE_Z, "F")
    return g


def layer4():
    g = empty()
    for x, z in TOWERS:
        setb(g, x, z, "W")
    setb(g, -3, GATE_Z, "O")
    setb(g, 3, GATE_Z, "O")
    for x in (-2, -1, 0, 1, 2):
        setb(g, x, GATE_Z, "X")
    setb(g, -2, GATE_Z + 1, "T")
    setb(g, 2, GATE_Z + 1, "T")
    return g


def layer5():
    g = empty()
    for x, z in TOWERS:
        setb(g, x, z, "N")
    setb(g, -3, GATE_Z, "Y")
    setb(g, 3, GATE_Z, "Y")
    for x in (-2, -1, 0, 1, 2):
        setb(g, x, GATE_Z, "X")
    return g


def layer6():
    g = empty()
    for x, z in TOWERS:
        setb(g, x, z, "S")
    for x in range(-3, 4):
        setb(g, x, GATE_Z, "O")
    setb(g, 0, GATE_Z, "H")
    setb(g, -3, GATE_Z, "Y")
    setb(g, 3, GATE_Z, "Y")
    return g


def layer7():
    g = empty()
    setb(g, -3, GATE_Z, "W")
    setb(g, 3, GATE_Z, "W")
    setb(g, 0, GATE_Z, "N")
    return g


def layer8():
    g = empty()
    setb(g, 0, GATE_Z, "S")
    return g


def main():
    layers = [
        ("0", layer0()),
        ("1", layer1()),
        ("2", layer2()),
        ("3", layer3()),
        ("4", layer4()),
        ("5", layer5()),
        ("6", layer6()),
        ("7", layer7()),
        ("8", layer8()),
    ]
    for name, g in layers:
        assert all(len(r) == SIZE for r in g), name
    pal = "\n".join(f"  {k}: {v}" for k, v in PALETTE.items())
    body = "\n".join(f'  "{n}":\n{dump(g)}' for n, g in layers)
    text = f"""# Цитадель Адского Разлома 25×25
# Сплошной пол для ИИ, врата портала на севере, 4 башни с огнём душ.
# Центр r<=7 свободен на y>=1 — босс ходит без препятствий.

palette:
{pal}

layers:
{body}

boss-spawn-offset:
  x: 0.0
  y: 1.0
  z: 0.0
  yaw: 180.0
  pitch: 0.0
"""
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as fh:
        fh.write(text)
    floor = sum(c != "." for row in layer0() for c in row)
    print("written", OUT, "floor", floor)


if __name__ == "__main__":
    main()
