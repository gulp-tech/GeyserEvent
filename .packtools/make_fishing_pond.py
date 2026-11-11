# -*- coding: utf-8 -*-
"""Пруд 21×21: вода, пирс, песок. Без высокой полки."""
from pathlib import Path

N = 21
CX = CZ = 10

PALETTE = {
    "W": "water",
    "S": "sand",
    "G": "grass_block",
    "O": "oak_planks",
    "K": "oak_log",
    "L": "lantern",
    "F": "oak_fence",
}


def grid(fill="."):
    return [[fill] * N for _ in range(N)]


def setc(g, x, z, ch):
    if 0 <= x < N and 0 <= z < N:
        g[z][x] = ch


def dump(g):
    return ["".join(row) for row in g]


layers = {y: grid(".") for y in range(3)}
g0, g1, g2 = layers[0], layers[1], layers[2]

for z in range(N):
    for x in range(N):
        dx, dz = x - CX, z - CZ
        d2 = dx * dx + dz * dz
        if d2 <= 36:
            setc(g0, x, z, "W")
        elif d2 <= 64:
            setc(g0, x, z, "S")
        else:
            setc(g0, x, z, "G")

# пирс с юга к центру
for z in range(CZ, N):
    for x in range(CX - 1, CX + 2):
        setc(g0, x, z, "O")
        if z > CZ + 1:
            setc(g1, x, z, ".")
for x in range(CX - 2, CX + 3):
    setc(g0, x, N - 2, "O")
    setc(g0, x, N - 1, "O")

# столбы фонарей на пирсе
for x, z in ((CX - 2, N - 2), (CX + 2, N - 2), (CX - 2, CZ + 3), (CX + 2, CZ + 3)):
    setc(g1, x, z, "K")
    setc(g2, x, z, "L")

# перила
for z in range(CZ + 2, N - 1):
    setc(g1, CX - 2, z, "F")
    setc(g1, CX + 2, z, "F")

out = Path(__file__).resolve().parents[1] / "src/main/resources/schematics/fishing_pond.schem.yml"
lines = [
    "# Пруд рыбалки 21×21. NPC на пирсе (юг).",
    "boss-spawn-offset:",
    "  x: 0.5",
    "  y: 1.0",
    "  z: 8.5",
    "  yaw: 180.0",
    "  pitch: 0.0",
    "",
    "palette:",
]
for k, v in PALETTE.items():
    lines.append(f"  {k}: {v}")
lines.append("")
lines.append("layers:")
for key in range(3):
    lines.append(f'  "{key}":')
    for row in dump(layers[key]):
        lines.append(f'    - "{row}"')
    lines.append("")
out.write_text("\n".join(lines), encoding="utf-8")
print("wrote", out, out.stat().st_size)
