# -*- coding: utf-8 -*-
# Генератор схематики ивента «Вторжение Пришельцев» (посадочная площадь 25x25).
#
# Пишет src/main/resources/schematics/alien_landing.schem.yml в том же формате,
# что читает RiftSchematicManager.parseYamlSchematic():
#   palette — символ -> материал (по одному на строку);
#   layers  — уровень Y -> список строк (ASCII-поле, центр в середине);
#   blocks  — точечные 3D-блоки (используются для мелких деталей).
#
# Структура: открытая круглая арена — босс встаёт прямо на грунте в центре,
# окружение не давит и не ограничивает его перемещение. Вокруг:
#   Y=0 — диск-посадка: гладкий центр, био-зона, посадочное кольцо с рунами;
#   Y=1 — только пьедесталы сундуков (r≈9) и основания антенн (±7,±7);
#   Y=2 — антенны;
#   Y=3 — светящиеся вершины антенн + люминесценты над пьедесталами.
# Ничего не ставится в радиусе r<5 вокруг центра (вращение босса свободно).
import io, os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "schematics", "alien_landing.schem.yml")

RADIUS = 12          # радиус диска (25x25)
SIZE = RADIUS * 2 + 1
CENTER = RADIUS

# ---------------------------------------------------------------- палитра
# ВАЖНО: материалы выбраны так, чтобы их текстуры можно было безопасно
# переопределить в ресурспаке — эти блоки НЕ используются в схемах других
# ивентов (Адский Разлом, Раскопки, Котёл) и редко встречаются в постройках.
PALETTE = {
    "H": "DARK_PRISMARINE",     # обшивка корабля (alien_hull)
    "A": "PURPUR_BLOCK",        # сплав пришельцев (alien_alloy)
    "C": "CONDUIT",             # energy-ядро (alien_core)
    "B": "END_STONE_BRICKS",    # био-масса (alien_bio)
    "O": "OBSIDIAN",            # посадочное кольцо (без override)
    "K": "SCULK",               # био-пятна (без override)
    "V": "SCULK_VEIN",          # биониты (без override)
    "M": "AMETHYST_BLOCK",      # кристаллы (без override)
    "L": "SEA_LANTERN",         # посадочные огни (без override)
    "S": "SHROOMLIGHT",         # светящиеся вершины антенн (без override)
}
LEGEND = {
    "H": "обшивка корабля (alien_hull)",
    "A": "сплав пришельцев (alien_alloy)",
    "C": "energy-ядро (alien_core)",
    "B": "био-масса (alien_bio)",
    "O": "обсидиан посадочного кольца",
    "K": "скалк-пятна",
    "V": "биониты (sculk vein)",
    "M": "amethystные кристаллы",
    "L": "морские фонари (посадочные огни)",
    "S": "грибной свет (вершины антенн)",
}

# точки деталей
PEDESTALS = [(0, -9), (9, 0), (0, 9), (-9, 0)]      # пьедесталы сундуков (x, z), r=9
ANTENNAE = [(-7, -7), (7, -7), (7, 7), (-7, 7)]     # антенны (x, z), d≈9.9
RING_LIGHTS = [(0, -12), (12, 0), (0, 12), (-12, 0)]  # огни на посадочном кольце


def dist(x, z):
    return (x * x + z * z) ** 0.5


def build_grid(level):
    """Возвращает SIZE x SIZE символов для уровня Y=level."""
    grid = [["." for _ in range(SIZE)] for _ in range(SIZE)]
    for x in range(-RADIUS, RADIUS + 1):
        for z in range(-RADIUS, RADIUS + 1):
            d = dist(x, z)
            if d > RADIUS + 0.4:
                continue
            cell = "."

            if level == 0:
                # ---- открытая площадка: центр гладкий, без возвышений ----
                if d <= 6.0:
                    cell = "H"                                   # гладкий диск
                    if abs(d - 4.6) < 0.6:
                        cell = "A"                               # декоративное кольцо сплава
                    elif (x * 3 + z * 5) % 17 == 0 and d > 3.0:
                        cell = "K"                               # био-пятна скалк
                elif d <= 10.0:
                    cell = "B"                                   # био-зона
                    if (x * 7 + z * 2) % 23 == 0:
                        cell = "M"                               # кристаллы
                else:
                    cell = "O"                                   # посадочное кольцо
                    for rx, rz in [(0, -11), (8, -8), (11, 0), (8, 8),
                                   (0, 11), (-8, 8), (-11, 0), (-8, -8)]:
                        if x == rx and z == rz:
                            cell = "C"                           # руны кольца
                    for lx, lz in RING_LIGHTS:
                        if x == lx and z == lz:
                            cell = "L"                           # посадочные огни

            elif level == 1:
                # ---- только пьедесталы и основания антенн; центр пуст ----
                if (x, z) in PEDESTALS:
                    cell = "A"                                   # пьедестал сундука
                elif (x, z) in ANTENNAE:
                    cell = "H"                                   # основание антенны
                if 6.0 < d <= 10.0 and (x * 5 + z * 3) % 19 == 0:
                    cell = "V"                                   # биониты
                if 6.0 < d <= 10.0 and (x * 11 + z) % 29 == 0:
                    cell = "M"                                   # кристаллы в био-зоне

            elif level == 2:
                # ---- антенны ----
                if (x, z) in ANTENNAE:
                    cell = "H"

            elif level == 3:
                # ---- светящиеся вершины антенн ----
                if (x, z) in ANTENNAE:
                    cell = "S"
                if (x, z) in PEDESTALS:
                    cell = "L"                                   # свет над пьедесталами

            if cell != ".":
                grid[z + CENTER][x + CENTER] = cell

    return grid


def rows_of(grid):
    return ["".join(row) for row in grid]


def main():
    lines = []
    a = lines.append
    a("# ============================================================================")
    a("# Схематика ивента «Вторжение Пришельцев» (посадочная площадь 25x25)")
    a("# OPEN ARENA: босс появляется по центру на открытой площадке (Y=0),")
    a("# без капсулы, стены или оболочки — перемещение по площади не ограничено.")
    a("# По центру (0.5, 1.0, 0.5) появляется босс-пришелец, вокруг — 4 пьедестала,")
    a("# на которых после его смерти плагин ставит сундуки с ресурсами.")
    a("# Генератор: .packtools/make_alien_schematic.py")
    a("# ============================================================================")
    a("")
    a('name: "alien_landing"')
    a('description: "Открытая посадочная площадь пришельцев: гладкий диск, био-зона, 4 пьедестала сундуков и 4 антенны"')
    a("")
    a("# Точка появления босса (относительно центра ивента)")
    a("boss-spawn-offset:")
    a("  # смещение от центра: босс встаёт в центр открытой площадки")
    a("  x: 0.0")
    a("  y: 1.0")
    a("  z: 0.0")
    a("  yaw: 0.0")
    a("  pitch: 0.0")
    a("")
    a("# Палитра символов")
    a("palette:")
    for sym, mat in PALETTE.items():
        a("  '%s': \"%s\"" % (sym, mat))
    a("")
    a("# Уровни (Y относительно центра ивента, центр поля — середина строки)")
    a("layers:")
    for level in range(0, 4):
        a("  %d:" % level)
        for row in rows_of(build_grid(level)):
            a('    - "%s"' % row)
        a("")
    a("# Точные детали (биониты и кристаллы в био-зоне)")
    a("blocks:")
    detail = 0
    for x in range(-RADIUS, RADIUS + 1):
        for z in range(-RADIUS, RADIUS + 1):
            d = dist(x, z)
            if 6.0 < d <= 10.0 and (x * 13 + z * 7) % 31 == 0:
                a("  - { x: %d, y: 1, z: %d, material: \"SCULK_VEIN\" }" % (x, z))
                detail += 1
            elif 3.2 < d <= 9.0 and (x * 17 + z * 3) % 37 == 0:
                a("  - { x: %d, y: 1, z: %d, material: \"AMETHYST_CLUSTER\" }" % (x, z))
                detail += 1
    if detail == 0:
        a("  - { x: 0, y: 0, z: 0, material: \"PURPUR_BLOCK\" }")
    a("")

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    io.open(OUT, "w", encoding="utf-8").write("\n".join(lines) + "\n")
    print("написано:", OUT)
    print("строк:", len(lines), "| деталей:", detail)

    # превью в консоль
    for level in (0, 1, 2, 3):
        print("--- Y=%d ---" % level)
        for row in rows_of(build_grid(level)):
            print(row)


if __name__ == "__main__":
    main()
