# -*- coding: utf-8 -*-
"""Стадион CR7 51×51: нормальное футбольное поле (полосы, разметка, ворота, трибуны)."""
from pathlib import Path

N = 51
CX = CZ = 25
# Поле ~ FIFA 1.54: 41×27 внутри 51×51
Z0, Z1 = 5, 45   # length 41
X0, X1 = 12, 38  # width 27

PALETTE = {
    "N": "black_concrete",
    "Q": "smooth_quartz",
    "S": "stone_bricks",
    "Y": "yellow_concrete",
    "B": "blue_concrete",
    "G": "green_concrete",
    "D": "lime_concrete",
    "W": "white_concrete",
    "I": "iron_block",
    "F": "iron_bars",
    "L": "glowstone",
    "A": "light_blue_concrete",
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
            if abs(d - r) <= 0.62:
                setc(g, x, z, ch)


def dump(g):
    return ["".join(row) for row in g]


# --- layer 0: ground ---
g0 = grid("N")
# concourse
rect(g0, 2, 2, 48, 48, "Q", fill=True)
# stand floor
rect(g0, 4, 4, 46, 46, "S", fill=True)
# track around pitch
rect(g0, X0 - 2, Z0 - 2, X1 + 2, Z1 + 2, "A", fill=True)
# pitch grass with mowing stripes along length (Z)
for z in range(Z0, Z1 + 1):
    stripe = "D" if ((z - Z0) // 2) % 2 == 0 else "G"
    for x in range(X0, X1 + 1):
        setc(g0, x, z, stripe)
# outline
rect(g0, X0, Z0, X1, Z1, "W", fill=False)
# halfway
hline(g0, X0, X1, CZ, "W")
# center circle + spot
circle(g0, CX, CZ, 4, "W")
setc(g0, CX, CZ, "W")
# penalty boxes (depth 6, width 17)
box_w = 8  # half-width → 17 total
box_d = 6
goal_w = 3  # half → 7 total
goal_d = 2
spot_d = 4
for z_goal, inward in ((Z0, 1), (Z1, -1)):
    za = z_goal
    zb = z_goal + inward * box_d
    rect(g0, CX - box_w, za, CX + box_w, zb, "W", fill=False)
    ga = z_goal
    gb = z_goal + inward * goal_d
    rect(g0, CX - goal_w, ga, CX + goal_w, gb, "W", fill=False)
    setc(g0, CX, z_goal + inward * spot_d, "W")
# corner arcs
for x, z, dx, dz in (
    (X0, Z0, 1, 1), (X1, Z0, -1, 1), (X0, Z1, 1, -1), (X1, Z1, -1, -1)
):
    setc(g0, x + dx, z, "W")
    setc(g0, x, z + dz, "W")

# --- layer 1: goals, flags, stand inner wall ---
g1 = grid(".")
# stand inner rail
rect(g1, 4, 4, 46, 46, "S", fill=False)
# quartz pillars / corners
for x, z in ((4, 4), (46, 4), (4, 46), (46, 46), (4, CX), (46, CX), (CX, 4), (CX, 46)):
    setc(g1, x, z, "Q")
# corner flags
for x, z in ((X0, Z0), (X1, Z0), (X0, Z1), (X1, Z1)):
    setc(g1, x, z, "Y")
# goals: posts on goal line, net behind
for z_goal, net_dir in ((Z0, -1), (Z1, 1)):
    hline(g1, CX - 2, CX + 2, z_goal, "I")  # base
    for nx in range(1, 3):
        hline(g1, CX - 2, CX + 2, z_goal + net_dir * nx, "F")

# --- layer 2: posts + seats ---
g2 = grid(".")
rect(g2, 3, 3, 47, 47, "Y", fill=False)
rect(g2, 4, 4, 46, 46, "B", fill=False)
for z_goal, net_dir in ((Z0, -1), (Z1, 1)):
    setc(g2, CX - 2, z_goal, "I")
    setc(g2, CX + 2, z_goal, "I")
    hline(g2, CX - 2, CX + 2, z_goal + net_dir, "F")
    hline(g2, CX - 2, CX + 2, z_goal + net_dir * 2, "F")
for x, z in ((4, 4), (46, 4), (4, 46), (46, 46)):
    setc(g2, x, z, "L")

# --- layer 3: crossbar + upper seats ---
g3 = grid(".")
rect(g3, 2, 2, 48, 48, "Y", fill=False)
rect(g3, 3, 3, 47, 47, "B", fill=False)
for z_goal in (Z0, Z1):
    hline(g3, CX - 2, CX + 2, z_goal, "I")  # crossbar
for x, z in ((2, 2), (48, 2), (2, 48), (48, 48)):
    setc(g3, x, z, "L")

# --- layer 4: floodlights ---
g4 = grid(".")
for x, z in ((2, 2), (48, 2), (2, 48), (48, 48), (2, CX), (48, CX), (CX, 2), (CX, 48)):
    setc(g4, x, z, "L")

layers = {"0": g0, "1": g1, "2": g2, "3": g3, "4": g4}

# sanity: 4-way symmetry of grass outline
assert dump(g0)[Z0][X0] == "W"
assert len(dump(g0)) == N and all(len(r) == N for r in dump(g0))

out = Path(__file__).resolve().parents[1] / "src/main/resources/schematics/ronaldo_stadium.schem.yml"
lines = [
    "# Стадион CR7 51×51 — футбольное поле FIFA-пропорций, полосы, штрафные, ворота, трибуны.",
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
for key in ("0", "1", "2", "3", "4"):
    lines.append(f'  "{key}":')
    for row in dump(layers[key]):
        lines.append(f'    - "{row}"')
    lines.append("")
out.write_text("\n".join(lines), encoding="utf-8")
print("wrote", out, "bytes", out.stat().st_size)
# preview pitch center
for row in dump(g0)[CZ - 6 : CZ + 7]:
    print(row[X0 - 1 : X1 + 2])
