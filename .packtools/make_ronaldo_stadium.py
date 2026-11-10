# -*- coding: utf-8 -*-
"""Компактное поле CR7 37×37: газон, разметка, низкие бордюры, простые ворота. Без огромной полки."""
from pathlib import Path

N = 37
CX = CZ = 18
Z0, Z1 = 5, 31
X0, X1 = 7, 29

PALETTE = {
    "G": "green_concrete",
    "D": "lime_concrete",
    "W": "white_concrete",
    "S": "stone_bricks",
    "Y": "yellow_concrete",
    "N": "white_stained_glass_pane",
    "X": "red_wool",
}


def grid(fill="."):
    return [[fill] * N for _ in range(N)]


def inb(x, z):
    return 0 <= x < N and 0 <= z < N


def setc(g, x, z, ch):
    if inb(x, z):
        g[z][x] = ch


def hline(g, x0, x1, z, ch):
    for x in range(min(x0, x1), max(x0, x1) + 1):
        setc(g, x, z, ch)


def rect(g, x0, z0, x1, z1, ch, fill=False):
    xa, xb = min(x0, x1), max(x0, x1)
    za, zb = min(z0, z1), max(z0, z1)
    for z in range(za, zb + 1):
        for x in range(xa, xb + 1):
            if fill or z in (za, zb) or x in (xa, xb):
                setc(g, x, z, ch)


def circle(g, cx, cz, r, ch):
    for x in range(cx - r - 1, cx + r + 2):
        for z in range(cz - r - 1, cz + r + 2):
            d = ((x - cx) ** 2 + (z - cz) ** 2) ** 0.5
            if abs(d - r) <= 0.7:
                setc(g, x, z, ch)


def dump(g):
    return ["".join(row) for row in g]


def in_pitch(x, z):
    return X0 <= x <= X1 and Z0 <= z <= Z1


layers = {y: grid(".") for y in range(5)}
g0 = layers[0]
for z in range(N):
    for x in range(N):
        if in_pitch(x, z):
            setc(g0, x, z, "D" if ((z - Z0) // 2) % 2 == 0 else "G")
        elif X0 - 1 <= x <= X1 + 1 and Z0 - 1 <= z <= Z1 + 1:
            setc(g0, x, z, "S")

rect(g0, X0, Z0, X1, Z1, "W", fill=False)
hline(g0, X0, X1, CZ, "W")
circle(g0, CX, CZ, 4, "W")
setc(g0, CX, CZ, "W")
box_w, box_d = 7, 5
goal_w, goal_d = 3, 2
for z_goal, inward in ((Z0, 1), (Z1, -1)):
    rect(g0, CX - box_w, z_goal, CX + box_w, z_goal + inward * box_d, "W", fill=False)
    rect(g0, CX - goal_w, z_goal, CX + goal_w, z_goal + inward * goal_d, "W", fill=False)
    setc(g0, CX, z_goal + inward * 4, "W")

# низкий бордюр сидений — один ряд, без террас в гору
g1 = layers[1]
for z in range(N):
    for x in range(N):
        if in_pitch(x, z):
            continue
        if X0 - 1 <= x <= X1 + 1 and Z0 - 1 <= z <= Z1 + 1:
            setc(g1, x, z, "Y")

# ворота: стойки + перекладина на y=3, сетка сзади (не коробка)
def build_goal(z_line, net_dir):
    left, right = CX - 2, CX + 2
    for y in (1, 2, 3):
        setc(layers[y], left, z_line, "W")
        setc(layers[y], right, z_line, "W")
    hline(layers[3], left, right, z_line, "W")
    zb = z_line + net_dir
    for x in range(left, right + 1):
        for y in (1, 2, 3):
            setc(layers[y], x, zb, "N")
    for y in (1, 2):
        setc(layers[y], left, zb, "N")
        setc(layers[y], right, zb, "N")


build_goal(Z0, -1)
build_goal(Z1, 1)

for x, z in ((X0, Z0), (X1, Z0), (X0, Z1), (X1, Z1)):
    setc(layers[1], x, z, "X")

assert dump(layers[1])[Z0][CX] == ".", dump(layers[1])[Z0]
assert dump(layers[1])[Z0][CX - 2] == "W"
assert dump(layers[2])[Z0][CX] == ".", "рот ворот не должен быть стеной"
assert dump(layers[3])[Z0][CX] == "W"

out = Path(__file__).resolve().parents[1] / "src/main/resources/schematics/ronaldo_stadium.schem.yml"
lines = [
    "# Поле CR7 37×37 — газон, разметка, простые ворота. Без внешней полки.",
    "boss-spawn-offset:",
    "  x: 0.5",
    "  y: 1.0",
    "  z: 0.5",
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
print("goal y1", dump(layers[1])[Z0][CX - 4: CX + 5])
print("goal y2", dump(layers[2])[Z0][CX - 4: CX + 5])
print("goal y3", dump(layers[3])[Z0][CX - 4: CX + 5])
print("net  y1", dump(layers[1])[Z0 - 1][CX - 4: CX + 5])
