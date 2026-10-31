#!/usr/bin/env python3
"""
Alien invasion landing-site schematic generator (v1.20.2 — BEAUTIFIED).

Design goals:
  * Center truly open — boss spawns on natural terrain, NO schematic blocks within r<5.
    The player called the previous grass disc + rings a "capsule"; now the center is empty.
  * Beautiful arena — multi-layered rings with height variation, glowing elements,
    crystal formations, organic sculk veins, pedestals and antennae.
  * Symmetrical and interesting from any viewing angle.
  * All boss-spawn-offset remains (0, 1.0, 0) — feet at Y=1, standing on natural Y=0 terrain.

Rings (Y=0 ground):
  r<5.0        → EMPTY (natural terrain preserved)
  5.0–5.5      → H  dark_prismarine   (alien_hull — smooth inner ring)
  5.5–6.5      → A  purpur_block      (alien_alloy — decorative ring)
  6.5–7.5      → B  end_stone_bricks  (alien_bio — bio zone)
  7.5–8.5      → B  end_stone_bricks  (with K sculk patches)
  8.5–9.5      → B  end_stone_bricks  (with M amethyst crystal bases)
  9.5–10.5     → O  obsidian          (with C conduit runes at 8 positions)
  10.5–12      → O  obsidian          (with L sea_lantern at 4 cardinals)

Y=1 (first layer above ground):
  r<5          → EMPTY
  r≈5          → H pillars at cardinals (0,±5), (±5,0)
  r=6–7        → C conduit energy field (embedded runes)
  r=7–9        → M amethyst_cluster + V sculk_vein (organic texture)
  r=9          → A purpur_block pedestals at cardinals (0,±9), (±9,0)
  r≈9.9        → H dark_prismarine antenna bases at corners (±7,±7)

Y=2 (second layer):
  r<5          → EMPTY
  r=7–9        → M amethyst_cluster tips (continue crystals)
  r≈9.9        → H dark_prismarine antenna shafts

Y=3 (third layer):
  r<5          → EMPTY
  r≈9.9        → S shroomlight antenna tips
  r=9          → L sea_lantern above pedestals
"""

from __future__ import annotations

import math
import os
import sys

# ---------------------------------------------------------------------------
# Palette
# ---------------------------------------------------------------------------
A = "purpur_block"               # alloy — decorative ring
B = "end_stone_bricks"           # bio — main arena floor
C = "conduit"                    # rune — glowing green energy
G = "grass_block"                # (kept for reference — NOT placed in center anymore)
H = "dark_prismarine"            # hull — dark structural
K = "sculk"                      # organic patches
L = "sea_lantern"                # light — glowing white
M = "amethyst_block"             # crystal — glowing purple
O = "obsidian"                   # outer ring — dark barrier
S = "shroomlight"                # antenna tip — glowing mushroom light
V = "sculk_vein"                  # organic vein texture

PALETTE = {
    "A": A, "B": B, "C": C, "G": G, "H": H, "K": K, "L": L,
    "M": M, "O": O, "S": S, "V": V,
}

# ---------------------------------------------------------------------------
# Geometry
# ---------------------------------------------------------------------------
RADIUS = 12
SIZE = 2 * RADIUS + 1               # 25×25
CENTER = RADIUS

# Pedestal positions (cardinal points at r=9)
PEDESTALS = [(0, -9), (9, 0), (0, 9), (-9, 0)]
# Antenna positions (corners at r≈9.9)
ANTENNAE = [(-7, -7), (7, -7), (7, 7), (-7, 7)]
# Outer ring light positions (cardinal points at r=11)
RING_LIGHTS = [(0, -11), (11, 0), (0, 11), (-11, 0)]
# Conduit rune positions (8 positions at r≈10)
CONDUIT_RUNES = [
    (0, -10), (7, -7), (10, 0), (7, 7),
    (0, 10), (-7, 7), (-10, 0), (-7, -7),
]

# Cardinal pillar positions at r=5
INNER_PILLARS = [(0, -5), (5, 0), (0, 5), (-5, 0)]


def _inside(x, z):
    """True if position is inside the 25×25 grid."""
    return 0 <= x < SIZE and 0 <= z < SIZE


def _set(grid, x, z, symbol):
    if _inside(x, z):
        grid[z][x] = symbol


def build_grid(level: int) -> list[list[str]]:
    """Build a 25×25 symbol grid for a given Y level."""
    grid = [["." for _ in range(SIZE)] for _ in range(SIZE)]
    cx = CENTER
    cz = CENTER

    for x in range(-RADIUS, RADIUS + 1):
        for z in range(-RADIUS, RADIUS + 1):
            d = math.sqrt(x * x + z * z)
            px = cx + x
            pz = cz + z

            if level == 0:
                _build_ground(grid, px, pz, x, z, d)
            elif level == 1:
                _build_layer1(grid, px, pz, x, z, d)
            elif level == 2:
                _build_layer2(grid, px, pz, x, z, d)
            elif level == 3:
                _build_layer3(grid, px, pz, x, z, d)

    return grid


def _build_ground(grid, px, pz, x, z, d):
    """Y=0 — the ground layer."""
    # Center truly empty — natural terrain, boss stands here
    if d < 5.0:
        return

    # Inner ring — smooth dark_prismarine
    if d <= 5.5:
        _set(grid, px, pz, "H")
        return

    # Alloy ring — purpur
    if d <= 6.5:
        _set(grid, px, pz, "A")
        return

    # Bio zone — end_stone_bricks with organic patches
    if d <= 9.5:
        # Sculk patches — organic texture in the bio zone
        if 7.5 < d <= 8.5 and (x * 3 + z * 7) % 5 == 0:
            _set(grid, px, pz, "K")
            return
        # Amethyst crystal bases — glowing purple
        if 8.5 < d <= 9.5 and (x + z) % 4 == 0:
            _set(grid, px, pz, "M")
            return
        _set(grid, px, pz, "B")
        return

    # Obsidian ring with conduit runes and sea lanterns
    if d <= 12:
        # Sea lantern lights at cardinal points
        if (x, z) in RING_LIGHTS:
            _set(grid, px, pz, "L")
            return
        # Conduit runes at 8 positions
        if (x, z) in CONDUIT_RUNES:
            _set(grid, px, pz, "C")
            return
        _set(grid, px, pz, "O")


def _build_layer1(grid, px, pz, x, z, d):
    """Y=1 — first layer above ground."""
    # Center truly empty
    if d < 5.0:
        return

    # Inner pillars at cardinal points (r=5)
    if (x, z) in INNER_PILLARS:
        _set(grid, px, pz, "H")
        return

    # Conduit energy field — embedded runes in the alloy ring
    if 5.5 < d <= 7.0 and (x + z) % 3 == 0:
        _set(grid, px, pz, "C")
        return

    # Bio zone — amethyst crystals and sculk veins
    if 6.5 < d <= 9.5:
        # Amethyst cluster bases (tall crystals)
        if 7.0 < d <= 9.0 and (x * 2 + z) % 3 == 0:
            _set(grid, px, pz, "M")
            return
        # Sculk veins — organic texture
        if 7.5 < d <= 8.5 and (x * 3 + z * 2) % 4 == 0:
            _set(grid, px, pz, "V")
            return
        return

    # Pedestals at cardinal points (r=9)
    if (x, z) in PEDESTALS:
        _set(grid, px, pz, "A")
        return

    # Antenna bases at corners (r≈9.9)
    if (x, z) in ANTENNAE:
        _set(grid, px, pz, "H")


def _build_layer2(grid, px, pz, x, z, d):
    """Y=2 — second layer."""
    # Center truly empty
    if d < 5.0:
        return

    # Amethyst cluster tips — continue crystals from Y=1
    if 7.0 < d <= 9.0 and (x * 2 + z) % 3 == 0:
        _set(grid, px, pz, "M")
        return

    # Antenna shafts at corners
    if (x, z) in ANTENNAE:
        _set(grid, px, pz, "H")


def _build_layer3(grid, px, pz, x, z, d):
    """Y=3 — third layer."""
    # Center truly empty
    if d < 5.0:
        return

    # Antenna tips — shroomlight glowing
    if (x, z) in ANTENNAE:
        _set(grid, px, pz, "S")
        return

    # Sea lantern above pedestals
    if (x, z) in PEDESTALS:
        _set(grid, px, pz, "L")


def rows_of(grid):
    """Return grid rows as list of strings."""
    return ["".join(row) for row in grid]


def write_schematic(path: str) -> dict:
    """Generate and write the YAML schematic (palette + layers format)."""
    layers = {}
    for level in range(4):
        grid = build_grid(level)
        layers[level] = rows_of(grid)

    spawn = {"x": 0.0, "y": 1.0, "z": 0.0, "yaw": 0.0, "pitch": 0.0}

    # Count entries
    count = 0
    for level, rows in layers.items():
        for row in rows:
            for symbol in row:
                if symbol != ".":
                    count += 1

    # Build YAML text
    lines = [
        "# Alien landing-site arena — v1.20.2 (beautiful, open center)",
        "# Center r<5 is EMPTY — boss spawns on natural terrain, no capsule.",
        "# Multi-layered arena: dark_prismarine / purpur / end_stone_bricks",
        "# / obsidian rings with conduit runes, amethyst crystals, sea lanterns.",
        "# Height variation: pedestals (Y=1), crystals (Y=1-2), antennae (Y=1-3).",
        "",
        "palette:",
    ]
    for sym, mat in sorted(PALETTE.items()):
        lines.append(f"  {sym}: {mat}")

    lines.append("")
    lines.append("layers:")
    for level in range(4):
        lines.append(f'  "{level}":')
        for row in layers[level]:
            lines.append(f'    - "{row}"')

    lines.append("")
    lines.append("boss-spawn-offset:")
    lines.append(f"  x: {spawn['x']}")
    lines.append(f"  y: {spawn['y']}")
    lines.append(f"  z: {spawn['z']}")
    lines.append(f"  yaw: {spawn['yaw']}")
    lines.append(f"  pitch: {spawn['pitch']}")
    lines.append("")

    text = "\n".join(lines)
    with open(path, "w") as f:
        f.write(text)
    return {
        "entries": count,
        "path": path,
        "size": SIZE,
    }


if __name__ == "__main__":
    # Default output path
    default_out = os.path.join(
        os.path.dirname(os.path.abspath(__file__)),
        "..",
        "src",
        "main",
        "resources",
        "schematics",
        "alien_landing.schem.yml",
    )
    out = sys.argv[1] if len(sys.argv) > 1 else default_out
    result = write_schematic(out)
    print(f"Schematic written to {result['path']}")
    print(f"  entries: {result['entries']}")
    print(f"  size: {result['size']}×{result['size']}")
    print(f"  boss spawn offset: (0.0, 1.0, 0.0)")
    print()
    print("=== Y=0 (ground layer) ===")
    grid = build_grid(0)
    for row in rows_of(grid):
        print(row)
    print()
    print("=== Y=1 (first layer) ===")
    grid = build_grid(1)
    for row in rows_of(grid):
        print(row)
    print()
    print("=== Y=2 (second layer) ===")
    grid = build_grid(2)
    for row in rows_of(grid):
        print(row)
    print()
    print("=== Y=3 (third layer) ===")
    grid = build_grid(3)
    for row in rows_of(grid):
        print(row)
    print()
    # Verify center is truly empty
    grid0 = build_grid(0)
    print("=== Center 11×11 (x/z -5..5) at Y=0 — should be ALL '.' ===")
    for r in rows_of(grid0)[CENTER-5:CENTER+6]:
        print(r[CENTER-5:CENTER+6])
    center_block = grid0[CENTER][CENTER]
    print(f"\nCenter Y=0 block: '{center_block}' (should be '.')")
    print("Boss spawns at Y=1, standing on natural Y=0 terrain — NO capsule.")
