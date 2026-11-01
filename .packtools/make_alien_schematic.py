#!/usr/bin/env python3
"""
Alien invasion landing-site schematic generator (v1.20.3 — REDESIGNED).

Design goals:
  * Boss can escape: NO full enclosure. The arena is an open platform with
    gaps and low walls — boss walks freely in all directions.
  * Beautiful: angular alien-tech aesthetic (not boring concentric rings).
    Star-shaped platform, geometric patterns, crystal spires, energy fields.
  * Center open: boss spawns on natural terrain, NO blocks within r<4.
  * All boss-spawn-offset remains (0, 1.0, 0) — feet at Y=1, standing on Y=0.

Design:
  Y=0 (ground):
    - Center r<4: EMPTY (natural terrain)
    - Star-shaped platform: 8-pointed star with angular edges
    - Dark prismarine base, purpur accents, end_stone_bricks fill
    - Glowing conduit runes at star points
    - Scattered amethyst crystal bases
    - NO continuous wall — gaps everywhere so boss can walk out

  Y=1 (first layer):
    - Angular pillars at star points (not a wall — individual pillars)
    - Crystal clusters rising from the ground
    - Pedestals at 4 cardinal points
    - Sculk veins for organic texture

  Y=2 (second layer):
    - Crystal spire tips
    - Antenna shafts at corners
    - Floating lantern elements

  Y=3 (third layer):
    - Shroomlight antenna tips
    - Sea lantern above pedestals
    - Floating glow elements
"""

from __future__ import annotations

import math
import os
import sys

# ---------------------------------------------------------------------------
# Palette
# ---------------------------------------------------------------------------
A = "purpur_block"               # alloy — decorative accents
B = "end_stone_bricks"           # bio — platform fill
C = "conduit"                    # rune — glowing green energy
H = "dark_prismarine"            # hull — dark structural base
K = "sculk"                      # organic patches
L = "sea_lantern"                # light — glowing white
M = "amethyst_block"             # crystal — glowing purple
O = "obsidian"                   # accent — dark highlights
S = "shroomlight"                # antenna tip — glowing mushroom light
V = "sculk_vein"                  # organic vein texture
W = "amethyst_cluster"           # crystal cluster (taller, more detailed)

PALETTE = {
    "A": A, "B": B, "C": C, "H": H, "K": K, "L": L,
    "M": M, "O": O, "S": S, "V": V, "W": W,
}

# ---------------------------------------------------------------------------
# Geometry
# ---------------------------------------------------------------------------
RADIUS = 12
SIZE = 2 * RADIUS + 1               # 25×25
CENTER = RADIUS

# Star points (8 points at angles 0, 45, 90, ... 315)
STAR_ANGLES = [i * math.pi / 4 for i in range(8)]
STAR_RADIUS = 10.0
STAR_INNER_RADIUS = 6.5

# Pedestal positions (cardinal points at r=7)
PEDESTALS = [(0, -7), (7, 0), (0, 7), (-7, 0)]

# Crystal cluster positions (at star inner points)
CRYSTAL_CLUSTERS = [
    (5, 0), (0, 5), (-5, 0), (0, -5),
    (4, 4), (-4, 4), (-4, -4), (4, -4),
]

# Antenna positions (at star outer points)
ANTENNAE = [
    (7, 7), (7, -7), (-7, 7), (-7, -7),
    (10, 0), (0, 10), (-10, 0), (0, -10),
]

# Conduit rune positions (at star midpoints)
CONDUIT_RUNES = [
    (7, 0), (5, 5), (0, 7), (-5, 5),
    (-7, 0), (-5, -5), (0, -7), (5, -5),
]

# Inner pillar positions (at r=5, between star points)
INNER_PILLARS = [
    (3, 4), (4, 3), (-3, 4), (-4, 3),
    (3, -4), (4, -3), (-3, -4), (-4, -3),
]


def _inside(x, z):
    """True if position is inside the 25×25 grid."""
    return 0 <= x < SIZE and 0 <= z < SIZE


def _set(grid, x, z, symbol):
    if _inside(x, z):
        grid[z][x] = symbol


def _star_shape(x, z):
    """Returns True if (x,z) is inside the 8-pointed star shape."""
    d = math.sqrt(x * x + z * z)
    if d > STAR_RADIUS + 0.5:
        return False
    if d < STAR_INNER_RADIUS:
        return True
    # Check angle — in star regions
    angle = math.atan2(z, x)
    # Normalize to 0..2π
    if angle < 0:
        angle += 2 * math.pi
    # Star has points at multiples of π/4
    # Between points, the boundary is at STAR_INNER_RADIUS
    # At points, the boundary is at STAR_RADIUS
    # Check if we're in a "point" region (closer to a star angle)
    for sa in STAR_ANGLES:
        sa_norm = sa if sa >= 0 else sa + 2 * math.pi
        diff = abs(angle - sa_norm)
        if diff > math.pi:
            diff = 2 * math.pi - diff
        if diff < math.pi / 8:  # within 22.5° of a star point
            # In a point — allow up to STAR_RADIUS
            return d <= STAR_RADIUS + 0.5
    # Between points — use inner radius
    return d <= STAR_INNER_RADIUS + 0.5


def build_grid(level: int) -> list[list[str]]:
    """Build a 25×25 symbol grid for a given Y level."""
    grid = [["." for _ in range(SIZE)] for _ in range(SIZE)]
    cx = CENTER
    cz = CENTER

    for x in range(-RADIUS, RADIUS + 1):
        for z in range(-RADIUS, RADIUS + 1):
            px = cx + x
            pz = cz + z

            if level == 0:
                _build_ground(grid, px, pz, x, z)
            elif level == 1:
                _build_layer1(grid, px, pz, x, z)
            elif level == 2:
                _build_layer2(grid, px, pz, x, z)
            elif level == 3:
                _build_layer3(grid, px, pz, x, z)

    return grid


def _build_ground(grid, px, pz, x, z):
    """Y=0 — the ground layer. Star-shaped platform, open center."""
    d = math.sqrt(x * x + z * z)

    # Center truly empty — natural terrain, boss stands here
    if d < 4.0:
        return

    # Star-shaped platform
    if _star_shape(x, z):
        # Conduit runes at star midpoints
        if (x, z) in CONDUIT_RUNES:
            _set(grid, px, pz, "C")
            return
        # Obsidian accents at star points
        for sa in STAR_ANGLES:
            diff = abs(math.atan2(z, x) - sa)
            if diff > math.pi:
                diff = 2 * math.pi - diff
            if diff < math.pi / 16 and d > STAR_RADIUS - 1.5:
                _set(grid, px, pz, "O")
                return
        # Dark prismarine base for the inner part of the star
        if d <= 6.5:
            _set(grid, px, pz, "H")
            return
        # Purpur accents in the outer star points
        if d > 6.5 and d <= STAR_RADIUS:
            # Check if near a star point angle
            angle = math.atan2(z, x)
            for sa in STAR_ANGLES:
                diff = abs(angle - sa)
                if diff > math.pi:
                    diff = 2 * math.pi - diff
                if diff < math.pi / 8:
                    _set(grid, px, pz, "A")
                    return
            _set(grid, px, pz, "B")
            return
        _set(grid, px, pz, "B")
        return

    # Scattered elements OUTSIDE the star (decoration, not wall)
    # Small amethyst crystal bases scattered around
    if d <= 11.5 and (x * 3 + z * 7) % 11 == 0 and (x, z) not in ANTENNAE:
        _set(grid, px, pz, "M")
        return

    # Sculk patches for organic texture outside the star
    if d <= 11.0 and (x * 5 + z * 3) % 7 == 0:
        _set(grid, px, pz, "K")


def _build_layer1(grid, px, pz, x, z):
    """Y=1 — first layer above ground."""
    d = math.sqrt(x * x + z * z)

    # Center truly empty
    if d < 4.0:
        return

    # Inner pillars at star inner points (individual, not walls)
    if (x, z) in INNER_PILLARS:
        _set(grid, px, pz, "H")
        return

    # Crystal cluster bases
    if (x, z) in CRYSTAL_CLUSTERS:
        _set(grid, px, pz, "W")
        return

    # Crystal clusters at scattered positions
    if 5.0 < d <= 9.0 and (x * 2 + z * 3) % 5 == 0 and (x, z) not in PEDESTALS:
        _set(grid, px, pz, "M")
        return

    # Pedestals at cardinal points
    if (x, z) in PEDESTALS:
        _set(grid, px, pz, "A")
        return

    # Sculk veins on the platform
    if _star_shape(x, z) and d > 5.0 and (x * 3 + z * 2) % 6 == 0:
        _set(grid, px, pz, "V")


def _build_layer2(grid, px, pz, x, z):
    """Y=2 — second layer."""
    d = math.sqrt(x * x + z * z)

    # Center truly empty
    if d < 4.0:
        return

    # Crystal cluster tips — continue crystals from Y=1
    if (x, z) in CRYSTAL_CLUSTERS:
        _set(grid, px, pz, "W")
        return

    # Crystal tips at scattered positions
    if 5.0 < d <= 9.0 and (x * 2 + z * 3) % 5 == 0 and (x, z) not in PEDESTALS:
        _set(grid, px, pz, "M")
        return

    # Antenna shafts at star outer points
    if (x, z) in ANTENNAE:
        _set(grid, px, pz, "H")


def _build_layer3(grid, px, pz, x, z):
    """Y=3 — third layer."""
    d = math.sqrt(x * x + z * z)

    # Center truly empty
    if d < 4.0:
        return

    # Shroomlight antenna tips
    if (x, z) in ANTENNAE:
        _set(grid, px, pz, "S")
        return

    # Sea lantern above pedestals
    if (x, z) in PEDESTALS:
        _set(grid, px, pz, "L")
        return

    # Crystal spire tips
    if (x, z) in CRYSTAL_CLUSTERS:
        _set(grid, px, pz, "W")


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
        "# Alien landing-site arena — v1.20.3 (REDESIGNED: star-shaped, open)",
        "# Center r<4 is EMPTY — boss spawns on natural terrain.",
        "# 8-pointed star platform with angular alien-tech aesthetic.",
        "# NO continuous wall — boss can walk freely in all directions.",
        "# Crystal spires, conduit runes, antennae, pedestals, sculk veins.",
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
