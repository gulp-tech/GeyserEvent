# -*- coding: utf-8 -*-
"""Стадион CR7 51×51: поле, нормальные ворота (стойки+перекладина+сетка), террасы, прожекторы."""
from pathlib import Path

N = 51
CX = CZ = 25
# Поле 35×29, запас за лицевой под сетку и трибуны
Z0, Z1 = 8, 42
X0, X1 = 11, 39

PALETTE = {
    "N": "black_concrete",
    "Q": "smooth_quartz",
    "P": "quartz_pillar",
    "S": "stone_bricks",
    "R": "red_concrete",
    "V": "green_terracotta",
    "Y": "yellow_concrete",
    "B": "blue_concrete",
    "G": "green_concrete",
    "D": "lime_concrete",
    "W": "white_concrete",
    "O": "orange_concrete",
    "F": "iron_bars",
    "A": "sea_lantern",
    "L": "glowstone",
    "U": "end_rod",
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


def vline(g, x, z0, z1, ch):
    for z in range(min(z0, z1), max(z0, z1) + 1):
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


def dist_to_pitch(x, z):
    if in_pitch(x, z):
        return 0
    dx = 0 if X0 <= x <= X1 else min(abs(x - X0), abs(x - X1))
    dz = 0 if Z0 <= z <= Z1 else min(abs(z - Z0), abs(z - Z1))
    if X0 <= x <= X1:
        return dz
    if Z0 <= z <= Z1:
        return dx
    return min(dx, dz)


def in_goal_mouth(x, z):
    if not (CX - 3 <= x <= CX + 3):
        return False
    return (Z0 - 2 <= z <= Z0) or (Z1 <= z <= Z1 + 2)


layers = {y: grid(".") for y in range(7)}

# --- y=0 ground ---
g0 = layers[0]
for z in range(N):
    for x in range(N):
        ring = min(x, z, N - 1 - x, N - 1 - z)
        d = dist_to_pitch(x, z)
        if ring <= 1:
            setc(g0, x, z, "N")
        elif in_pitch(x, z):
            stripe = "D" if ((z - Z0) // 2) % 2 == 0 else "G"
            setc(g0, x, z, stripe)
        elif d == 1:
            setc(g0, x, z, "O")
        elif ring == 2:
            setc(g0, x, z, "Q")
        else:
            setc(g0, x, z, "S")

rect(g0, X0, Z0, X1, Z1, "W", fill=False)
hline(g0, X0, X1, CZ, "W")
circle(g0, CX, CZ, 5, "W")
setc(g0, CX, CZ, "W")

box_w, box_d = 9, 7
goal_w, goal_d = 4, 3
spot_d = 5
for z_goal, inward in ((Z0, 1), (Z1, -1)):
    rect(g0, CX - box_w, z_goal, CX + box_w, z_goal + inward * box_d, "W", fill=False)
    rect(g0, CX - goal_w, z_goal, CX + goal_w, z_goal + inward * goal_d, "W", fill=False)
    setc(g0, CX, z_goal + inward * spot_d, "W")
for x, z, dx, dz in (
    (X0, Z0, 1, 1), (X1, Z0, -1, 1), (X0, Z1, 1, -1), (X1, Z1, -1, -1)
):
    setc(g0, x + dx, z, "W")
    setc(g0, x, z + dz, "W")
    setc(g0, x + dx, z + dz, "W")

# --- seats / walls y=1..4 ---
for y in range(1, 5):
    g = layers[y]
    for z in range(N):
        for x in range(N):
            ring = min(x, z, N - 1 - x, N - 1 - z)
            d = dist_to_pitch(x, z)
            if in_pitch(x, z) or in_goal_mouth(x, z):
                continue
            if ring <= 1:
                if y <= 3:
                    setc(g, x, z, "N")
                continue
            if ring == 2 and y <= 4:
                setc(g, x, z, "Q")
                continue
            # террасы: ближе к полю — ниже, сзади — выше
            if d == 1:
                continue
            max_y = 1 if d == 2 else (2 if d == 3 else (3 if d == 4 else 4))
            if y > max_y:
                continue
            if y == max_y:
                # сиденья: красные с юга, зелёные с севера, жёлтая кромка
                if d == 2:
                    setc(g, x, z, "Y")
                elif z < CZ:
                    setc(g, x, z, "R")
                else:
                    setc(g, x, z, "V")
            else:
                setc(g, x, z, "S")

# --- ворота: стойки + перекладина + сетка НАЗАД, рот открыт ---
def build_goal(z_line, net_dir):
    left, right = CX - 3, CX + 3
    for y in (1, 2, 3):
        setc(layers[y], left, z_line, "P")
        setc(layers[y], right, z_line, "P")
    hline(layers[4], left, right, z_line, "P")
    for step in (1, 2):
        z = z_line + net_dir * step
        for y in (1, 2, 3, 4):
            hline(layers[y], left, right, z, "F")
            setc(layers[y], left, z, "F")
            setc(layers[y], right, z, "F")
    # боковые стойки сетки к линии ворот
    for y in (1, 2, 3):
        setc(layers[y], left, z_line + net_dir, "F")
        setc(layers[y], right, z_line + net_dir, "F")


build_goal(Z0, -1)
build_goal(Z1, 1)

# угловые флаги
for x, z in ((X0, Z0), (X1, Z0), (X0, Z1), (X1, Z1)):
    setc(layers[1], x, z, "U")
    setc(layers[2], x, z, "X")

# прожекторы-башни
for x, z in ((2, 2), (48, 2), (2, 48), (48, 48), (2, CX), (48, CX), (CX, 2), (CX, 48)):
    for y in range(1, 5):
        setc(layers[y], x, z, "P")
    setc(layers[5], x, z, "A")
    setc(layers[6], x, z, "L")

# sanity
assert dump(g0)[Z0][X0] == "W"
assert dump(layers[1])[Z0][CX] == "."  # рот ворот открыт
assert dump(layers[1])[Z0][CX - 3] == "P"
assert dump(layers[4])[Z0][CX] == "P"
assert len(dump(g0)) == N

out = Path(__file__).resolve().parents[1] / "src/main/resources/schematics/ronaldo_stadium.schem.yml"
lines = [
    "# Стадион CR7 51×51 — поле, открытые ворота (стойки/перекладина/сетка), террасы, прожекторы.",
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
for key in range(7):
    lines.append(f'  "{key}":')
    for row in dump(layers[key]):
        lines.append(f'    - "{row}"')
    lines.append("")
out.write_text("\n".join(lines), encoding="utf-8")
print("wrote", out, out.stat().st_size)
print("goal line y1:", dump(layers[1])[Z0][CX - 5: CX + 6])
print("crossbar y4:", dump(layers[4])[Z0][CX - 5: CX + 6])
print("net back y2:", dump(layers[2])[Z0 - 2][CX - 5: CX + 6])
print("pitch mid:", dump(g0)[CZ][X0 - 1: X1 + 2])
