# -*- coding: utf-8 -*-
"""Изометрический превью-рендер схемы alien_landing.schem.yml.

Рисует посадочную площадку пришельцев в PNG (без PIL: свой PNG-writer),
чтобы было видно структуру без запуска сервера. Также выводит внизу
полоску кастомных текстур корабля и подписывает пьедесталы сундуков.

Запуск: python3 .packtools/render_alien_preview.py
"""
import io, os, struct, zlib, random, sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
sys.path.insert(0, HERE)

from make_alien_textures import read_png  # noqa: E402

SCHEM = os.path.join(REPO, "src", "main", "resources", "schematics", "alien_landing.schem.yml")
TEX_DIR = os.path.join(REPO, "src", "main", "resources", "resourcepack-vanilla",
                       "textures", "blocks", "geyserevent")
OUT = os.path.join(REPO, "alien_landing_preview.png")

# ---------------------------------------------------------------- цвета блоков
# Средний цвет из сгенерированных текстур корабля + ванильные近似 для остального.
BLOCK_COLORS = {
    "DARK_PRISMARINE": (28, 52, 48),      # обшивка (alien_hull)
    "PURPUR_BLOCK": (166, 100, 190),      # сплав (alien_alloy)
    "CONDUIT": (96, 200, 190),            # энерго-ядро (alien_core)
    "END_STONE_BRICKS": (196, 200, 150),  # био-масса (alien_bio)
    "OBSIDIAN": (24, 18, 38),
    "SCULK": (30, 42, 48),
    "SCULK_VEIN": (38, 58, 62),
    "AMETHYST_BLOCK": (140, 110, 200),
    "AMETHYST_CLUSTER": (170, 140, 230),
    "SEA_LANTERN": (200, 225, 235),
    "SHROOMLIGHT": (230, 190, 150),
}

W, H = 1000, 800
TILE_W, TILE_H = 26, 15          # пол-плитки в изометрии
BLOCK_H = 13                     # высота блока в пикселях
SCALE_TEX = 4                    # масштаб полоски текстур

BG_TOP = (8, 10, 22)
BG_BOTTOM = (18, 12, 34)


# ---------------------------------------------------------------- мини-хелперы
def _chunk(tag, data):
    return (struct.pack(">I", len(data)) + tag + data
            + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))


def write_png(path, width, height, pixels):
    raw = bytearray()
    for y in range(height):
        raw.append(0)
        for x in range(width):
            raw.extend(pixels[y][x])
    png = b"\x89PNG\r\n\x1a\n"
    png += _chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
    png += _chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    png += _chunk(b"IEND", b"")
    io.open(path, "wb").write(png)
    return len(png)


def shade(color, k):
    return (max(0, min(255, int(color[0] * k))),
            max(0, min(255, int(color[1] * k))),
            max(0, min(255, int(color[2] * k))),
            255)


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))



# ---------------------------------------------------------------- мини-шрифт 3x5
FONT = {
    "А": (".#.", "#.#", "###", "#.#", "#.#"),
    "Б": ("###", "#..", "###", "..#", "###"),
    "В": ("###", "#..", "##.", "#..", "###"),
    "Г": ("###", "#..", "#..", "#..", "#.."),
    "Д": (".##", "#.#", "#.#", "#.#", "###"),
    "Е": ("###", "#..", "##.", "#..", "###"),
    "Ж": ("#.#", "#.#", "###", "#.#", "#.#"),
    "З": ("###", "..#", ".##", "..#", "###"),
    "И": ("#.#", "#.#", "#.#", "#.#", "###"),
    "К": ("#.#", "#.#", "##.", "#.#", "#.#"),
    "Л": ("###", "#.#", "#.#", "#.#", "#.#"),
    "М": ("#.#", "###", "###", "#.#", "#.#"),
    "Н": ("#.#", "#.#", "###", "#.#", "#.#"),
    "О": ("###", "#.#", "#.#", "#.#", "###"),
    "П": ("###", "#.#", "#.#", "#.#", "#.#"),
    "Р": ("###", "#.#", "###", "#..", "#.."),
    "С": ("###", "#..", "#..", "#..", "###"),
    "Т": ("###", ".#.", ".#.", ".#.", ".#."),
    "У": ("#.#", "#.#", "###", "..#", "##."),
    "Ф": (".#.", "###", "#.#", "###", ".#."),
    "Х": ("#.#", "#.#", ".#.", "#.#", "#.#"),
    "Ц": ("#.#", "#.#", "#.#", "#.#", "###"),
    "Ч": ("#.#", "#.#", "###", "..#", "..#"),
    "Ш": ("#.#", "#.#", "#.#", "#.#", "###"),
    "Щ": ("#.#", "#.#", "#.#", "#.#", "###"),
    "Ъ": ("##.", ".#.", "###", ".#.", "###"),
    "Ы": ("#.#", "#.#", "###", "#.#", "###"),
    "Ь": ("#..", "#..", "###", "#.#", "###"),
    "Э": ("###", "..#", "###", "..#", "###"),
    "Ю": ("#.#", "#.#", "###", "#.#", "#.#"),
    "Я": ("###", "#.#", "###", "..#", "..#"),
    "0": ("###", "#.#", "#.#", "#.#", "###"),
    "1": (".#.", "##.", ".#.", ".#.", "###"),
    "2": ("###", "..#", "###", "#..", "###"),
    "3": ("###", "..#", "###", "..#", "###"),
    "4": ("#.#", "#.#", "###", "..#", "..#"),
    "5": ("###", "#..", "###", "..#", "###"),
    "6": ("###", "#..", "###", "#.#", "###"),
    "7": ("###", "..#", "..#", "..#", "..#"),
    "8": ("###", "#.#", "###", "#.#", "###"),
    "9": ("###", "#.#", "###", "..#", "..#"),
    " ": ("...", "...", "...", "...", "..."),
    "-": ("...", "...", "###", "...", "..."),
    "X": ("#.#", "#.#", "###", "#.#", "#.#"),
    "X4": None,
}


def draw_text(pix, x, y, text, color, scale=2, gap=1):
    cx = x
    for ch in text:
        glyph = FONT.get(ch.upper())
        if glyph is None:
            cx += 4 * scale
            continue
        for gy in range(5):
            for gx in range(3):
                if glyph[gy][gx] == "#":
                    for dy in range(scale):
                        for dx in range(scale):
                            px_, py_ = cx + gx * scale + dx, y + gy * scale + dy
                            if 0 <= px_ < W and 0 <= py_ < H:
                                pix[py_][px_] = color
        cx += (3 + gap) * scale
    return cx


# ---------------------------------------------------------------- разбор схемы
def load_blocks():
    """Возвращает {(x,y,z): material} + смещение босса."""
    import yaml
    doc = yaml.safe_load(io.open(SCHEM, encoding="utf-8").read())
    palette = {k: v for k, v in doc["palette"].items()}
    blocks = {}
    for y_key, rows in doc["layers"].items():
        dy = int(y_key)
        half_z = len(rows) // 2
        for r, row in enumerate(rows):
            compact = row.replace(" ", "")
            half_x = len(compact) // 2
            dz = r - half_z
            for c, sym in enumerate(compact):
                if sym == ".":
                    continue
                mat = palette.get(sym)
                if mat and mat != "AIR":
                    blocks[(c - half_x, dy, dz)] = mat
    for entry in doc.get("blocks", []) or []:
        mat = str(entry.get("material"))
        blocks[(int(entry.get("x", 0)), int(entry.get("y", 0)), int(entry.get("z", 0)))] = mat
    return blocks, doc.get("boss-spawn-offset", {}), doc


# ---------------------------------------------------------------- проекция
def project(x, y, z, ox, oy):
    """Изометрия: камера сверху-справа-спереди."""
    sx = ox + (x - z) * (TILE_W / 2.0)
    sy = oy + (x + z) * (TILE_H / 2.0) - y * BLOCK_H
    return sx, sy


def draw_cube(pix, x, y, z, color, ox, oy, alpha_top=1.0):
    cx, cy = project(x, y, z, ox, oy)
    cx, cy = int(round(cx)), int(round(cy))

    def put(px, py, col, a=1.0):
        if 0 <= px < W and 0 <= py < H:
            base = pix[py][px]
            if a >= 1.0:
                pix[py][px] = col
            else:
                pix[py][px] = (int(base[0] + (col[0] - base[0]) * a),
                               int(base[1] + (col[1] - base[1]) * a),
                               int(base[2] + (col[2] - base[2]) * a), 255)

    hw, hh = TILE_W / 2.0, TILE_H / 2.0
    # верхняя грань (ромб)
    for row in range(TILE_H):
        t = row / float(TILE_H - 1)
        span = hw * (1 - abs(2 * t - 1)) * 2
        x0 = cx - span / 2
        for i in range(int(round(span))):
            put(int(round(x0 + i)), cy - int(TILE_H / 2) + row, shade(color, 1.0 + 0.0))
    # левая грань
    for row in range(BLOCK_H):
        t = row / float(BLOCK_H - 1)
        span = hw * (1 - t)
        y0 = cy + hh + row
        for i in range(int(round(span * 2))):
            put(int(round(cx - span + i)), int(y0), shade(color, 0.62))
    # правая грань
    for row in range(BLOCK_H):
        t = row / float(BLOCK_H - 1)
        span = hw * (1 - t)
        y0 = cy + hh + row
        for i in range(int(round(span * 2))):
            put(int(round(cx + i)), int(y0), shade(color, 0.82))


# ---------------------------------------------------------------- основной рендер
def main():
    blocks, boss_off, doc = load_blocks()
    if not blocks:
        raise SystemExit("Схема пуста — нечего рисовать")

    # центр по X/Z, минимальный Y
    xs = [k[0] for k in blocks]
    zs = [k[2] for k in blocks]
    ys = [k[1] for k in blocks]
    cx0, cz0 = (min(xs) + max(xs)) / 2.0, (min(zs) + max(zs)) / 2.0
    y0 = min(ys)

    # подбираем смещение, чтобы структура влезла
    span_x = (max(xs) - min(xs) + max(zs) - min(zs)) * TILE_W / 2.0 + TILE_W * 2
    ox = W / 2.0 - ((cx0 - cz0) * (TILE_W / 2.0))
    oy = 250 + (y0 * BLOCK_H) - ((cx0 + cz0) * (TILE_H / 2.0))

    pix = [[BG_TOP for _ in range(W)] for _ in range(H)]
    # фон: градиент + звёзды
    rnd = random.Random(20240)
    for y in range(H):
        row = lerp(BG_TOP, BG_BOTTOM, y / float(H - 1))
        for x in range(W):
            pix[y][x] = (row[0], row[1], row[2], 255)
    for _ in range(320):
        x, y = rnd.randrange(W), rnd.randrange(0, int(H * 0.75))
        b = rnd.randrange(90, 220)
        pix[y][x] = (b, b, min(255, b + 25), 255)

    # порядок отрисовки: дальние сначала
    order = sorted(blocks.keys(), key=lambda k: (k[0] + k[2] + k[1]))
    for (x, y, z) in order:
        mat = blocks[(x, y, z)]
        color = BLOCK_COLORS.get(mat, (120, 120, 120))
        draw_cube(pix, x, y, z, color, ox, oy)

    # --- подсветка: 4 пьедестала сундуков и место босса ---
    chest_offsets = [(0, 9), (9, 0), (0, -9), (-9, 0)]
    boss_x = float(boss_off.get("x", 0.0))
    boss_z = float(boss_off.get("z", 0.0))

    def marker(px, py, color, size=7):
        for dy in range(-size, size + 1):
            for dx in range(-size, size + 1):
                if dx * dx + dy * dy <= size * size:
                    if 0 <= px + dx < W and 0 <= py + dy < H:
                        pix[py + dy][px + dx] = color

    for (dx, dz) in chest_offsets:
        px, py = project(dx, 2, dz, ox, oy)
        marker(int(px), int(py - 4), (255, 205, 70, 255), 5)
    px, py = project(boss_x, 1, boss_z, ox, oy)
    marker(int(px), int(py - 10), (120, 255, 140, 255), 6)

    # --- полоска кастомных текстур корабля ---
    strip_y = H - 130
    names = ["alien_hull", "alien_alloy", "alien_core", "alien_bio"]
    labels = ["обшивка", "сплав", "энерго-ядро", "био-масса"]
    tx = 40
    for name, label in zip(names, labels):
        path = os.path.join(TEX_DIR, name + ".png")
        w, h, rows = read_png(path)
        # масштаб 4x
        for yy in range(h * SCALE_TEX):
            for xx in range(w * SCALE_TEX):
                c = rows[yy // SCALE_TEX][xx // SCALE_TEX]
                X, Y = tx + xx, strip_y + yy
                if 0 <= X < W and 0 <= Y < H:
                    pix[Y][X] = (c[0], c[1], c[2], 255)
        # рамка
        for xx in range(w * SCALE_TEX):
            for Y in (strip_y - 1, strip_y + h * SCALE_TEX):
                if 0 <= tx + xx < W and 0 <= Y < H:
                    pix[Y][tx + xx] = (90, 110, 130, 255)
        for yy in range(h * SCALE_TEX):
            for X in (tx - 1, tx + w * SCALE_TEX):
                if 0 <= X < W and 0 <= strip_y + yy < H:
                    pix[strip_y + yy][X] = (90, 110, 130, 255)
        tx += w * SCALE_TEX + 34

    # --- подписи ---
    draw_text(pix, 40, 34, "ВТОРЖЕНИЕ ПРИШЕЛЬЦЕВ", (120, 255, 170, 255), scale=4)
    draw_text(pix, 40, 62, "ПОСАДОЧНАЯ ПЛОЩАДКА 25X25 - КАСТОМНЫЙ БОСС И 4 СУНДУКА",
              (150, 170, 200, 255), scale=2)

    # подпись у маркера босса
    bx, by = project(boss_x, 1, boss_z, ox, oy)
    draw_text(pix, int(bx) + 12, int(by) - 34, "БОСС", (120, 255, 140, 255), scale=2)
    # подпись у одного из сундуков
    sx, sy = project(0, 2, 9, ox, oy)
    draw_text(pix, int(sx) + 12, int(sy) - 26, "СУНДУКИ X4", (255, 205, 70, 255), scale=2)

    # подписи текстур
    tx = 40
    for name, label in zip(names, labels):
        draw_text(pix, tx, strip_y + 70, label.upper(), (150, 170, 200, 255), scale=1)
        tx += 16 * SCALE_TEX + 34

    size = write_png(OUT, W, H, pix)
    print("написано:", OUT, size, "байт")
    print("блоков в схеме:", len(blocks))
    print("размер структуры: %dx%d блоков, уровни Y=%d..%d"
          % (max(xs) - min(xs) + 1, max(zs) - min(zs) + 1, min(ys), max(ys)))


if __name__ == "__main__":
    main()


# ---------------------------------------------------------------- превью текстур
def render_texture_preview():
    """Крупным планом показывает лицо и спину пришельца + текстуры блоков."""
    import yaml
    from make_alien_textures import read_png

    W2, H2 = 900, 300
    pix = [[(14, 16, 26, 255) for _ in range(W2)] for _ in range(H2)]
    for _ in range(160):
        x, y = random.Random(7).randrange(W2), random.Random(7).randrange(H2)
        b = random.Random(x * 31 + y).randrange(60, 140)
        pix[y][x] = (b, b, min(255, b + 20), 255)

    ev = os.path.join(REPO, "src", "main", "resources", "resourcepack-vanilla",
                      "textures", "entity", "illager", "evoker.png")
    w, h, px = read_png(ev)

    blocks = [
        ("ЛИЦО (пасть + глаза)", (8, 8, 15, 17), (150, 255, 90)),
        ("СПИНА (руки сзади)", (30, 26, 37, 37), (120, 255, 190)),
        ("ЗАТЫЛОК", (16, 8, 23, 17), (150, 170, 200)),
    ]
    x = 30
    scale = 16
    for title, (x0, y0, x1, y1), tint in blocks:
        bw, bh = x1 - x0 + 1, y1 - y0 + 1
        for yy in range(bh * scale):
            for xx in range(bw * scale):
                c = px[y0 + yy // scale][x0 + xx // scale]
                X, Y = x + xx, 70 + yy
                if 0 <= X < W2 and 0 <= Y < H2:
                    pix[Y][X] = (c[0], c[1], c[2], 255)
        for xx in range(bw * scale + 2):
            for Y in (69, 70 + bh * scale):
                if 0 <= x - 1 + xx < W2 and 0 <= Y < H2:
                    pix[Y][x - 1 + xx] = (70, 90, 110, 255)
        for yy in range(bh * scale + 2):
            for X in (x - 1, x + bw * scale):
                if 0 <= X < W2 and 0 <= 69 + yy < H2:
                    pix[69 + yy][X] = (70, 90, 110, 255)
        draw_text(pix, x, 40, title, tint, scale=2)
        draw_text(pix, x, 70 + bh * scale + 12, "%dX%d ПИКСЕЛЕЙ" % (bw, bh), (110, 130, 150), scale=1)
        x += bw * scale + 60

    out = os.path.join(REPO, "alien_textures_preview.png")
    size = write_png(out, W2, H2, pix)
    print("написано:", out, size, "байт")


render_texture_preview()
