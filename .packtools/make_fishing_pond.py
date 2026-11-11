# -*- coding: utf-8 -*-
"""Пруд 25×25: вода, пирс, будка рыбака, камыш, кусты."""
from pathlib import Path

N = 25
CX = CZ = 12

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


layers = {y: grid(".") for y in range(5)}
g0, g1, g2, g3, g4 = layers[0], layers[1], layers[2], layers[3], layers[4]

for z in range(N):
    for x in range(N):
        dx, dz = x - CX, z - CZ
        d2 = dx * dx + dz * dz
        if d2 <= 42:
            setc(g0, x, z, "W")
        elif d2 <= 72:
            setc(g0, x, z, "S")
        else:
            setc(g0, x, z, "G")

# пирс с юга к воде
for z in range(CZ, N - 1):
    for x in range(CX - 1, CX + 2):
        setc(g0, x, z, "O")
for x in range(CX - 3, CX + 4):
    setc(g0, x, N - 4, "O")
    setc(g0, x, N - 3, "O")
    setc(g0, x, N - 2, "O")

# перила пирса
for z in range(CZ + 2, N - 4):
    setc(g1, CX - 2, z, "F")
    setc(g1, CX + 2, z, "F")

# фонари на пирсе
for x, z in ((CX - 2, CZ + 3), (CX + 2, CZ + 3)):
    setc(g1, x, z, "K")
    setc(g2, x, z, "L")

# будка рыбака на южном конце пирса (вход на север, к воде)
HX0, HX1 = CX - 2, CX + 2
HZ0, HZ1 = N - 5, N - 2
for z in range(HZ0, HZ1 + 1):
    for x in range(HX0, HX1 + 1):
        setc(g0, x, z, "O")

for z in range(HZ0, HZ1 + 1):
    for x in range(HX0, HX1 + 1):
        edge = x in (HX0, HX1) or z in (HZ0, HZ1)
        door = z == HZ0 and x == CX
        if edge and not door:
            setc(g1, x, z, "K" if x in (HX0, HX1) and z in (HZ0, HZ1) else "I")
            setc(g2, x, z, "I")
        elif not edge:
            setc(g1, x, z, ".")
            setc(g2, x, z, ".")

setc(g1, HX0 + 1, HZ1 - 1, "B")
setc(g1, HX1 - 1, HZ1 - 1, "B")
setc(g2, CX, HZ1, "L")

# крыша
for z in range(HZ0, HZ1 + 1):
    for x in range(HX0, HX1 + 1):
        setc(g3, x, z, "O")
for x in range(HX0 + 1, HX1):
    setc(g4, x, HZ0 + 1, "V")
    setc(g4, x, HZ0 + 2, "V")
setc(g4, CX, HZ0 + 1, "L")

# камыш на песке у кромки
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
        if g0[z][x] == "W" and h(x, z + 2, 5) and abs(x - CX) > 1:
            setc(g1, x, z, "P")

# трава, папоротник, кусты, мох
for z in range(N):
    for x in range(N):
        if g0[z][x] != "G":
            continue
        if g1[z][x] != ".":
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
    "# Пруд рыбалки 25×25. Будка рыбака на юге пирса, камыш и кусты.",
    "boss-spawn-offset:",
    "  x: 0.5",
    "  y: 1.0",
    "  z: 9.5",
    "  yaw: 0.0",
    "  pitch: 0.0",
    "",
    "palette:",
]
for k, v in PALETTE.items():
    lines.append(f"  {k}: {v}")
lines.append("")
lines.append("layers:")
for key in range(5):
    lines.append(f'  "{key}":')
    for row in dump(layers[key]):
        lines.append(f'    - "{row}"')
    lines.append("")
out.write_text("\n".join(lines), encoding="utf-8")
print("wrote", out, out.stat().st_size)
