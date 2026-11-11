# -*- coding: utf-8 -*-
"""Пруд 31×31: широкая будка, забор по пирсу, камыш и кусты."""
from pathlib import Path

N = 31
CX = CZ = 15

PALETTE = {
    "W": "water",
    "S": "sand",
    "G": "grass_block",
    "O": "oak_planks",
    "K": "oak_log",
    "L": "lantern",
    "F": "oak_fence",
    "C": "sugar_cane",
    "T": "tall_grass",
    "E": "fern",
    "P": "lily_pad",
    "B": "barrel",
    "R": "oak_leaves",
    "A": "azalea",
    "M": "moss_block",
    "V": "oak_slab",
    "I": "stripped_oak_log",
}


def grid(fill="."):
    return [[fill] * N for _ in range(N)]


def setc(g, x, z, ch):
    if 0 <= x < N and 0 <= z < N:
        g[z][x] = ch


def dump(g):
    return ["".join(row) for row in g]


def h(x, z, mod):
    return (x * 17 + z * 31 + 9) % mod == 0


layers = {y: grid(".") for y in range(6)}
g0, g1, g2, g3, g4, g5 = (layers[y] for y in range(6))

for z in range(N):
    for x in range(N):
        dx, dz = x - CX, z - CZ
        d2 = dx * dx + dz * dz
        if d2 <= 80:
            setc(g0, x, z, "W")
        elif d2 <= 120:
            setc(g0, x, z, "S")
        else:
            setc(g0, x, z, "G")

# широкий пирс
for z in range(CZ + 1, N - 6):
    for x in range(CX - 2, CX + 3):
        setc(g0, x, z, "O")

# площадка будки
HX0, HX1 = CX - 4, CX + 4
HZ0, HZ1 = N - 7, N - 2
for z in range(HZ0, HZ1 + 1):
    for x in range(HX0, HX1 + 1):
        setc(g0, x, z, "O")

# забор вдоль пирса (смыкается сам)
for z in range(CZ + 2, HZ0):
    setc(g1, CX - 3, z, "F")
    setc(g1, CX + 3, z, "F")
for x in range(CX - 3, CX + 4):
    if x not in range(CX - 2, CX + 3):
        setc(g1, x, CZ + 2, "F")

# столбы-фонари по забору
for x, z in ((CX - 3, CZ + 3), (CX + 3, CZ + 3), (CX - 3, HZ0 - 1), (CX + 3, HZ0 - 1)):
    setc(g1, x, z, "K")
    setc(g2, x, z, "L")

# будка 9×6, стены в 3 блока, дверь 3 блока на север (к воде)
for z in range(HZ0, HZ1 + 1):
    for x in range(HX0, HX1 + 1):
        edge = x in (HX0, HX1) or z in (HZ0, HZ1)
        door = z == HZ0 and CX - 1 <= x <= CX + 1
        if edge and not door:
            corner = x in (HX0, HX1) and z in (HZ0, HZ1)
            wall = "K" if corner else "I"
            setc(g1, x, z, wall)
            setc(g2, x, z, wall)
            setc(g3, x, z, wall)

setc(g1, HX0 + 2, HZ1 - 1, "B")
setc(g1, HX1 - 2, HZ1 - 1, "B")
setc(g2, HX0 + 2, HZ1 - 1, "L")
setc(g2, HX1 - 2, HZ1 - 1, "L")

for z in range(HZ0, HZ1 + 1):
    for x in range(HX0, HX1 + 1):
        setc(g4, x, z, "O")
for z in range(HZ0 + 1, HZ1):
    for x in range(HX0 + 1, HX1):
        setc(g5, x, z, "V")
setc(g5, CX, HZ0 + 2, "L")

# камыш
for z in range(N):
    for x in range(N):
        if g0[z][x] != "S":
            continue
        if h(x, z, 3):
            setc(g1, x, z, "C")
            if h(x + 3, z, 2):
                setc(g2, x, z, "C")

# кувшинки
for z in range(N):
    for x in range(N):
        if g0[z][x] == "W" and h(x, z + 2, 5) and abs(x - CX) > 2:
            setc(g1, x, z, "P")

# трава и кусты
for z in range(N):
    for x in range(N):
        if g0[z][x] != "G" or g1[z][x] != ".":
            continue
        if h(x, z, 4):
            setc(g1, x, z, "T")
        elif h(x + 1, z, 5):
            setc(g1, x, z, "E")
        elif h(x, z + 4, 8):
            setc(g0, x, z, "M")
            setc(g1, x, z, "A")
        elif h(x + 5, z, 9):
            setc(g1, x, z, "R")
            setc(g2, x, z, "R")

out = Path(__file__).resolve().parents[1] / "src/main/resources/schematics/fishing_pond.schem.yml"
lines = [
    "# Пруд рыбалки 31×31. Большая будка, забор по пирсу.",
    "boss-spawn-offset:",
    "  x: 0.5",
    "  y: 1.0",
    "  z: 11.5",
    "  yaw: 180.0",
    "  pitch: 0.0",
    "",
    "palette:",
]
for k, v in PALETTE.items():
    lines.append(f"  {k}: {v}")
lines.append("")
lines.append("layers:")
for key in range(6):
    lines.append(f'  "{key}":')
    for row in dump(layers[key]):
        lines.append(f'    - "{row}"')
    lines.append("")
out.write_text("\n".join(lines), encoding="utf-8")
print("wrote", out, out.stat().st_size)
