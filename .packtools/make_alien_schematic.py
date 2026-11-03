#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Посадочная площадка «Вторжение Пришельцев» — плоский диск, чтобы ИИ ходил."""
from __future__ import annotations

import math
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "schematics", "alien_landing.schem.yml")

# H hull  A alloy  B bio  O obsidian  C conduit  L lantern
# S shroomlight  M amethyst  W cluster  K sculk
PALETTE = {
    "H": "dark_prismarine",
    "A": "purpur_block",
    "B": "end_stone_bricks",
    "O": "obsidian",
    "C": "conduit",
    "L": "sea_lantern",
    "S": "shroomlight",
    "M": "amethyst_block",
    "W": "amethyst_cluster",
    "K": "sculk",
}

RADIUS = 12
SIZE = 2 * RADIUS + 1  # 25
C = RADIUS

PEDESTALS = [(0, 9), (9, 0), (0, -9), (-9, 0)]
ANTENNAE = [(8, 8), (8, -8), (-8, 8), (-8, -8)]


def empty():
    return [["." for _ in range(SIZE)] for _ in range(SIZE)]


def setb(g, x, z, s):
    gx, gz = C + x, C + z
    if 0 <= gx < SIZE and 0 <= gz < SIZE:
        g[gz][gx] = s


def ring(g, r0, r1, s):
    for x in range(-RADIUS, RADIUS + 1):
        for z in range(-RADIUS, RADIUS + 1):
            d = math.hypot(x, z)
            if r0 - 0.5 <= d <= r1 + 0.5:
                setb(g, x, z, s)


def disk(g, r, s):
    for x in range(-RADIUS, RADIUS + 1):
        for z in range(-RADIUS, RADIUS + 1):
            if math.hypot(x, z) <= r + 0.15:
                setb(g, x, z, s)


def layer0():
    """Сплошной пол: мобы не проваливаются и pathfinding работает."""
    g = empty()
    disk(g, 11.2, "H")
    ring(g, 9.2, 10.2, "O")
    ring(g, 7.2, 8.2, "A")
    ring(g, 5.2, 6.2, "B")
    ring(g, 3.2, 4.2, "A")
    disk(g, 2.2, "H")
    setb(g, 0, 0, "C")
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        setb(g, dx, dz, "L")
    for x, z in PEDESTALS:
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                setb(g, x + dx, z + dz, "O")
        setb(g, x, z, "A")
    for x, z in ANTENNAE:
        setb(g, x, z, "H")
        setb(g, x + 1, z, "H")
        setb(g, x, z + 1, "H")
    # 4 прохода в ободе, чтобы босс мог выйти
    for x, z in ((0, 11), (0, -11), (11, 0), (-11, 0)):
        setb(g, x, z, "H")
    return g


def layer1():
    """Только декор по краю и пьедесталы. Центр пустой — ходить свободно."""
    g = empty()
    for x, z in PEDESTALS:
        setb(g, x, z, "L")
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            setb(g, x + dx, z + dz, "A")
    for x, z in ANTENNAE:
        setb(g, x, z, "H")
    # редкие кристаллы ТОЛЬКО на внешнем кольце, не на дорожке
    for ang in range(8):
        a = ang * math.pi / 4 + math.pi / 8
        x = int(round(math.cos(a) * 10))
        z = int(round(math.sin(a) * 10))
        if (x, z) not in PEDESTALS and (x, z) not in ANTENNAE:
            setb(g, x, z, "W")
    return g


def layer2():
    g = empty()
    for x, z in ANTENNAE:
        setb(g, x, z, "H")
    for x, z in PEDESTALS:
        setb(g, x, z, "M")
    return g


def layer3():
    g = empty()
    for x, z in ANTENNAE:
        setb(g, x, z, "S")
    return g


def dump_layer(g):
    lines = []
    for row in g:
        lines.append('    - "' + "".join(row) + '"')
    return "\n".join(lines)


def main():
    pal = "\n".join(f"  {k}: {v}" for k, v in PALETTE.items())
    text = f"""# Посадка пришельцев 25×25 — сплошной диск, свободный центр для ИИ.
# Y=0: пол (хитбокс pathfinding). Y=1+: только пьедесталы и антенны по краю.
# 4 пьедестала под сундуки: (0,±9), (±9,0). 4 антенны по диагоналям.

palette:
{pal}

layers:
  "0":
{dump_layer(layer0())}
  "1":
{dump_layer(layer1())}
  "2":
{dump_layer(layer2())}
  "3":
{dump_layer(layer3())}

boss-spawn-offset:
  x: 0.0
  y: 1.0
  z: 0.0
  yaw: 0.0
  pitch: 0.0
"""
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as fh:
        fh.write(text)
    # sanity: equal row lengths
    g0 = layer0()
    assert all(len(r) == SIZE for r in g0), "layer0 ragged"
    print("written", OUT, "size", SIZE, "floor blocks", sum(c != "." for row in g0 for c in row))


if __name__ == "__main__":
    main()
