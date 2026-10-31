# -*- coding: utf-8 -*-
# Генератор «пришельцев»: кастомные текстуры мобов + 4 текстуры блоков посадки.
#
# 1) Текстуры мобов — это ВАНИЛЬНЫЕ текстуры Bedrock (Mojang/bedrock-samples),
#    перекрашенные в пришельцевую палитру: форма и UV-развёртка остаются
#    ванильными, поэтому модель не ломается. Исходники лежат в
#    .packtools/vanilla_textures/ и перекрашиваются детерминированно.
#      textures/entity/illager/evoker.png  -> «Пришелец-Владыка» (босс)
#      textures/entity/illager/fangs.png    -> «Щупальца» (клыки эвокера)
#      textures/entity/vex/vex.png          -> «Малый пришелец» (приспешники)
#    ВАЖНО: ресурспак меняет текстуру ВСЕГО такого типа мобов в игре.
#    Эвокер и векс почти не встречаются в естественном мире (особняки/рейды),
#    поэтому побочный эффект минимален.
#
# 2) Текстуры блоков — 16x16 PNG, палитровые (цветовой тип 3), как у блоков
#    призыва: сотни байт на файл, «супер оптимизировано» без потери качества.
#    Блоки редкие и без механики, чтобы не ломать обычные постройки игроков:
#      gilded_blackstone  -> alien_hull   (обшивка корабля)
#      purpur_block       -> alien_alloy  (сплав пришельцев)
#      crying_obsidian    -> alien_core   (energy-ядро)
#      end_stone_bricks   -> alien_bio    (био-масса)
import io, os, struct, zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
VANILLA = os.path.join(ROOT, ".packtools", "vanilla_textures")
OUT_ENTITY = os.path.join(ROOT, "src", "main", "resources", "resourcepack-vanilla", "textures", "entity")
OUT_BLOCKS = os.path.join(ROOT, "src", "main", "resources", "resourcepack-vanilla",
                          "textures", "blocks", "geyserevent")


# ------------------------------------------------------------------ PNG decode
def read_png(path):
    """Возвращает (width, height, pixels RGBA list of tuples). Поддержка 8-бит,
    цветовые типы 2 (RGB), 6 (RGBA), 3 (палитра), фильтры 0-4."""
    data = io.open(path, "rb").read()
    assert data[:8] == b"\x89PNG\r\n\x1a\n", "не PNG: " + path
    pos = 8
    idat = bytearray()
    palette = []
    trns = []
    width = height = bit_depth = color_type = None
    while pos < len(data):
        length = struct.unpack(">I", data[pos:pos + 4])[0]
        tag = data[pos + 4:pos + 8]
        chunk = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if tag == b"IHDR":
            width, height, bit_depth, color_type = struct.unpack(">IIBB", chunk[:10])
            assert bit_depth == 8, "ожидался 8-бит"
        elif tag == b"PLTE":
            palette = [tuple(chunk[i:i + 3]) for i in range(0, len(chunk), 3)]
        elif tag == b"tRNS":
            trns = list(chunk)
        elif tag == b"IDAT":
            idat.extend(chunk)
        elif tag == b"IEND":
            break

    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[color_type]
    raw = zlib.decompress(bytes(idat))
    stride = width * channels
    out = bytearray()
    prev = bytearray(stride)
    p = 0
    for _y in range(height):
        f = raw[p]
        p += 1
        line = bytearray(raw[p:p + stride])
        p += stride
        for i in range(stride):
            a = line[i - channels] if i >= channels else 0
            b = prev[i]
            c = prev[i - channels] if i >= channels else 0
            if f == 0:
                pass
            elif f == 1:
                line[i] = (line[i] + a) & 0xFF
            elif f == 2:
                line[i] = (line[i] + b) & 0xFF
            elif f == 3:
                line[i] = (line[i] + ((a + b) >> 1)) & 0xFF
            elif f == 4:
                pp = a + b - c
                pa, pb, pc = abs(pp - a), abs(pp - b), abs(pp - c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 0xFF
            else:
                raise ValueError("неизвестный фильтр PNG: %d" % f)
        out.extend(line)
        prev = line

    pixels = []
    for y in range(height):
        row = []
        base = y * stride
        for x in range(width):
            i = base + x * channels
            if color_type == 2:
                row.append((out[i], out[i + 1], out[i + 2], 255))
            elif color_type == 6:
                row.append((out[i], out[i + 1], out[i + 2], out[i + 3]))
            elif color_type == 3:
                idx = out[i]
                r, g, b = palette[idx] if idx < len(palette) else (0, 0, 0)
                a = trns[idx] if idx < len(trns) else 255
                row.append((r, g, b, a))
            elif color_type == 0:
                v = out[i]
                row.append((v, v, v, 255))
            else:
                row.append((out[i], out[i + 1], 255, 255))
        pixels.append(row)
    return width, height, pixels


# ------------------------------------------------------------------ PNG encode
def _chunk(tag, data):
    return (struct.pack(">I", len(data)) + tag + data
            + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))


def write_png_rgba(path, width, height, pixels):
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


def write_png_palette(path, size, palette, pixels):
    raw = bytearray()
    for y in range(size):
        raw.append(0)
        raw.extend(pixels[y * size:(y + 1) * size])
    png = b"\x89PNG\r\n\x1a\n"
    png += _chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 3, 0, 0, 0))
    png += _chunk(b"PLTE", b"".join(bytes(c) for c in palette))
    png += _chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    png += _chunk(b"IEND", b"")
    io.open(path, "wb").write(png)
    return len(png)


# ------------------------------------------------------------------ перекраска
def _lerp(a, b, t):
    return a + (b - a) * t


def _gradient(stops, t):
    """stops: [(pos, (r,g,b)), ...] по возрастанию pos (0..1)."""
    if t <= stops[0][0]:
        return stops[0][1]
    for i in range(len(stops) - 1):
        p0, c0 = stops[i]
        p1, c1 = stops[i + 1]
        if t <= p1:
            k = 0.0 if p1 == p0 else (t - p0) / (p1 - p0)
            return tuple(int(round(_lerp(c0[j], c1[j], k))) for j in range(3))
    return stops[-1][1]


def alienify(pixels, stops, strength=0.72, glow=None, glow_thresh=0.82, glow_mix=0.6):
    """Перекрашивает текстуру: тёмное -> первый стоп, светлое -> последний.
    Яркие пиксели (глаза, блики) уводим в цвет свечения glow."""
    height = len(pixels)
    width = len(pixels[0])
    out = []
    for y in range(height):
        row = []
        for x in range(width):
            r, g, b, a = pixels[y][x]
            if a == 0:
                row.append((r, g, b, a))
                continue
            lum = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0
            target = _gradient(stops, lum)
            if glow is not None and lum >= glow_thresh:
                target = tuple(int(round(_lerp(target[j], glow[j], glow_mix))) for j in range(3))
            row.append((int(round(_lerp(r, target[0], strength))),
                        int(round(_lerp(g, target[1], strength))),
                        int(round(_lerp(b, target[2], strength))),
                        a))
        out.append(row)
    return out


# Палитры пришельцев: тень -> полутон -> свет
ALIEN_BOSS_STOPS = [
    (0.00, (6, 10, 14)),      # почти чёрный корпус
    (0.35, (18, 36, 40)),     # тёмный металл
    (0.70, (52, 90, 86)),     # тёмная броня
    (1.00, (160, 200, 180)),  # тусклый свет
]
ALIEN_MINION_STOPS = [
    (0.00, (8, 10, 12)),
    (0.35, (22, 36, 36)),
    (0.70, (56, 92, 82)),
    (1.00, (150, 190, 166)),
]
ALIEN_FANG_STOPS = [
    (0.00, (14, 10, 22)),
    (0.40, (44, 26, 66)),
    (0.75, (104, 62, 148)),
    (1.00, (206, 182, 240)),
]
GLOW_CYAN = (96, 255, 226)
GLOW_VIOLET = (186, 128, 255)

# Цвета «страшного» пришельца
MAW_DARK = (28, 3, 6, 255)        # глубина пасти — почти чёрная
MAW_RED = (138, 18, 16, 255)      # края пасти — кроваво-красные
TOOTH = (242, 238, 224, 255)      # зубы — ослепительно белые
EYE_GLOW = (255, 50, 30, 255)     # глаза — огненно-красные
EYE_SOCKET = (8, 8, 10, 255)
CLAW_GLOW = (255, 90, 50, 255)    # когти — красные
ARM_DARK = (10, 16, 20, 255)      # дополнительные руки — тёмные
ARM_LIGHT = (34, 60, 56, 255)
HORN_DARK = (16, 26, 30, 255)     # рога/шипы
HORN_LIGHT = (40, 60, 56, 255)
VEIN_GLOW = (90, 190, 180, 255)   # светящиеся вены на теле
SCAR = (130, 25, 18, 255)         # шрам на лбу

# ------------------------------------------------------------------ UV-раскладка
# Точные прямоугольники граней из моделей Mojang/bedrock-samples
# (resource_pack/models/entity/evoker.geo.json и vex.geo.json):
#   куб развёрнут как up/down + 4 стороны, стороны идут по порядку
#   east, south(лицо), north(спина), west.
EVOKER_UV = {
    "face":       (8, 8, 15, 17),    # лицо 8x10
    "head_back":  (16, 8, 23, 17),   # затылок
    "body_back":  (30, 26, 37, 37),  # спина корпуса 8x12
    "skirt_back": (14, 44, 21, 61),  # спина подола 8x18
    "arm_back":   (48, 50, 51, 61),  # спина рук 4x12
}
VEX_UV = {
    "face": (5, 5, 9, 9),            # лицо 5x5
    "body": (2, 12, 7, 22),
}


def _put(px, x, y, color):
    """Ставит пиксель, приводя цвет к RGBA (защита от 3-компонентных кортежей)."""
    if 0 <= y < len(px) and 0 <= x < len(px[0]):
        if len(color) == 3:
            color = (color[0], color[1], color[2], 255)
        px[y][x] = color


def _rect(px, box, color):
    """Заливает прямоугольник (x0, y0, x1, y1) цветом color."""
    x0, y0, x1, y1 = box
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            _put(px, x, y, color)


def _hline(px, x0, x1, y, color):
    for x in range(x0, x1 + 1):
        _put(px, x, y, color)


def paint_boss_face(px):
    """Страшная пасть с 3 рядами зубов + огненно-красные глаза на лице пришельца."""
    x0, y0, x1, y1 = EVOKER_UV["face"]
    w = x1 - x0 + 1                       # 8
    mid = x0 + w // 2                     # 12 — центр лица

    # 1. Глазницы (тёмные) + огненно-красные глаза (2x2)
    for ex in (x0 + 1, x0 + 4):           # левый (9,10) и правый (12,13) глаз
        _put(px, ex, y0 + 3, EYE_GLOW)
        _put(px, ex + 1, y0 + 3, EYE_GLOW)
        _put(px, ex, y0 + 4, EYE_GLOW)
        _put(px, ex + 1, y0 + 4, EYE_GLOW)

    # 2. Глазницы — тёмные границы вокруг глаз (360° обводка)
    for ex in (x0, x0 + 3, x1):           # левый край, между глазами, правый край
        for ey in range(y0 + 2, y0 + 5):  # rows 10, 11, 12, 13
            _put(px, ex, ey, EYE_SOCKET)
    for ex in (x0 + 1, x0 + 2, x0 + 4, x0 + 5):
        _put(px, ex, y0 + 2, EYE_SOCKET)
        _put(px, ex, y0 + 5, EYE_SOCKET)

    # 3. Брови-гребни (тёмные, угловатые)
    for ex in (x0 + 1, x0 + 2):
        _put(px, ex, y0 + 2, (28, 40, 38))
    for ex in (x0 + 4, x0 + 5):
        _put(px, ex, y0 + 2, (28, 40, 38))

    # 4. ПАСТЬ: огромная, 3 ряда зубов, занимает низ лица
    maw_x0, maw_x1 = x0 + 1, x1 - 1       # columns 9-14
    # верхняя губа / верх пасти
    for x in range(maw_x0, maw_x1 + 1):
        _put(px, x, y0 + 5, MAW_RED)      # row 13
    # зубы 1-й ряд
    for x in range(maw_x0, maw_x1 + 1):
        _put(px, x, y0 + 6, MAW_DARK)     # row 14
    for x in (maw_x0, maw_x0 + 2, maw_x0 + 4):
        _put(px, x, y0 + 6, TOOTH)        # teeth at 9, 11, 13
    # зубы 2-й ряд (сдвинуты)
    for x in range(maw_x0, maw_x1 + 1):
        _put(px, x, y0 + 7, MAW_DARK)     # row 15
    for x in (maw_x0 + 1, maw_x0 + 3, maw_x0 + 5):
        _put(px, x, y0 + 7, TOOTH)        # teeth at 10, 12, 14
    # зубы 3-й ряд
    for x in range(maw_x0, maw_x1 + 1):
        _put(px, x, y0 + 8, MAW_DARK)     # row 16
    for x in (maw_x0, maw_x0 + 2, maw_x0 + 4):
        _put(px, x, y0 + 8, TOOTH)        # teeth at 9, 11, 13
    # нижняя губа
    for x in range(maw_x0, maw_x1 + 1):
        _put(px, x, y0 + 9, MAW_RED)      # row 17

    # 5. Клыки по углам пасти (особенно длинные)
    _put(px, maw_x0, y0 + 5, TOOTH)       # top-left canine
    _put(px, maw_x1, y0 + 5, TOOTH)       # top-right canine
    _put(px, maw_x0, y0 + 6, TOOTH)       # left canine row 1
    _put(px, maw_x1, y0 + 6, TOOTH)       # right canine row 1

    # 6. Трещина по лбу (кровавый шрам)
    for i, y in enumerate(range(y0, y0 + 3)):
        _put(px, mid - 1 + (i % 2), y, SCAR)

    # 7. добавочные шипы по бокам лица (x7 и x16)
    _put(px, x0 - 1, y0 + 1, HORN_LIGHT)
    _put(px, x0 - 1, y0 + 2, HORN_DARK)
    _put(px, x0 - 1, y0 + 3, HORN_LIGHT)
    _put(px, x1 + 1, y0 + 1, HORN_LIGHT)
    _put(px, x1 + 1, y0 + 2, HORN_DARK)
    _put(px, x1 + 1, y0 + 3, HORN_LIGHT)


def paint_boss_back_arms(px):
    """Две дополнительные руки на спине + светящиеся вены и био-пластины."""
    x0, y0, x1, y1 = EVOKER_UV["body_back"]
    # правая и левая дополнительные руки по краям спины
    arms = [(x0, x0 + 2), (x1 - 2, x1)]
    for ax0, ax1 in arms:
        # плечо
        _rect(px, (ax0, y0, ax1, y0 + 1), ARM_LIGHT)
        # предплечье
        _rect(px, (ax0, y0 + 2, ax1, y0 + 8), ARM_DARK)
        # кисть
        _rect(px, (ax0, y0 + 9, ax1, y0 + 10), ARM_LIGHT)
        # 3 когти на каждой руке (вместо 1)
        for x in range(ax0, ax1 + 1):
            _put(px, x, y0 + 11, CLAW_GLOW)
        # металлический блеск
        for y in range(y0 + 2, y0 + 9, 3):
            _put(px, ax0, y, ARM_LIGHT)
    # позвоночные пластины по центру спины
    for y in range(y0 + 1, y1, 2):
        _put(px, x0 + 3, y, (36, 68, 62))
        _put(px, x0 + 4, y, (36, 68, 62))
    # светящиеся вены на спине
    for y in range(y0 + 2, y1, 2):
        _put(px, x0 + 2, y, VEIN_GLOW)
        _put(px, x0 + 5, y, VEIN_GLOW)
    # био-пластины (тёмные прямоугольники)
    _rect(px, (x0, y0, x0 + 1, y0 + 2), (18, 28, 30))
    _rect(px, (x1 - 1, y0, x1, y0 + 2), (18, 28, 30))
    _rect(px, (x0, y0 + 9, x0 + 1, y0 + 11), (18, 28, 30))
    _rect(px, (x1 - 1, y0 + 9, x1, y0 + 11), (18, 28, 30))


def paint_minion_face(px):
    """Два огненно-красных глаза + тёмная пасть с клыками малому пришельцу (векс)."""
    x0, y0, x1, y1 = VEX_UV["face"]
    # глазницы (тёмные) по краям и центру
    for ex in (x0, x0 + 2, x1):
        for ey in range(y0 + 1, y0 + 4):
            _put(px, ex, ey, EYE_SOCKET)
    # огненно-красные глаза (2x2)
    for ex in (x0 + 1, x1 - 1):
        _put(px, ex, y0 + 1, EYE_GLOW)
        _put(px, ex, y0 + 2, EYE_GLOW)
    # бровь-гребень
    for ex in (x0 + 1, x1 - 1):
        _put(px, ex, y0, (24, 36, 34))
    # тёмная пасть с клыками
    _hline(px, x0 + 1, x1 - 1, y0 + 3, (20, 4, 6))
    _put(px, x0 + 1, y0 + 2, TOOTH)   # клык слева
    _put(px, x1 - 1, y0 + 2, TOOTH)   # клык справа


def make_entity_textures():
    """Перекрашивает ванильные текстуры под пришельцев и дорисовывает детали."""
    made = []
    jobs = [
        ("evoker.png", os.path.join("illager", "evoker.png"), ALIEN_BOSS_STOPS, GLOW_CYAN, 0.74),
        ("fangs.png", os.path.join("illager", "fangs.png"), ALIEN_FANG_STOPS, GLOW_VIOLET, 0.70),
        ("vex.png", os.path.join("vex", "vex.png"), ALIEN_MINION_STOPS, GLOW_CYAN, 0.74),
    ]
    for src, rel, stops, glow, strength in jobs:
        w, h, px = read_png(os.path.join(VANILLA, src))
        out = alienify(px, stops, strength=strength, glow=glow)
        if src == "evoker.png":
            paint_boss_face(out)        # страшная пасть + светящиеся глаза
            paint_boss_back_arms(out)   # дополнительные руки на спине
        elif src == "vex.png":
            paint_minion_face(out)      # светящиеся глаза малому пришельцу
        dest = os.path.join(OUT_ENTITY, rel)
        os.makedirs(os.path.dirname(dest), exist_ok=True)
        size = write_png_rgba(dest, w, h, out)
        made.append((rel, size))
        print("%-34s %5d bytes (%dx%d)" % (rel, size, w, h))
    return made


# ------------------------------------------------------------------ блоки 16x16
SIZE = 16


class Canvas(object):
    def __init__(self, name):
        self.name = name
        self.palette = []
        self.pixels = [0] * (SIZE * SIZE)

    def color(self, r, g, b):
        c = (r, g, b)
        if c not in self.palette:
            self.palette.append(c)
        return self.palette.index(c)

    def px(self, x, y, idx):
        if 0 <= x < SIZE and 0 <= y < SIZE:
            self.pixels[y * SIZE + x] = idx

    def fill(self, idx):
        self.pixels = [idx] * (SIZE * SIZE)

    def rect(self, x0, y0, w, h, idx):
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                self.px(x, y, idx)

    def noise(self, colors, seed):
        for y in range(SIZE):
            for x in range(SIZE):
                h = (x * 73856093) ^ (y * 19349663) ^ (seed * 83492791)
                self.px(x, y, colors[abs(h) % len(colors)])

    def diamond(self, cx, cy, r, idx):
        for y in range(SIZE):
            for x in range(SIZE):
                if abs(x - cx) + abs(y - cy) <= r:
                    self.px(x, y, idx)

    def frame(self, idx):
        for i in range(SIZE):
            self.px(i, 0, idx)
            self.px(i, SIZE - 1, idx)
            self.px(0, i, idx)
            self.px(SIZE - 1, i, idx)

    def hline(self, x0, x1, y, idx):
        for x in range(x0, x1 + 1):
            self.px(x, y, idx)

    def vline(self, y0, y1, x, idx):
        for y in range(y0, y1 + 1):
            self.px(x, y, idx)

    def cracks(self, idx, points):
        for (x0, y0), (x1, y1) in zip(points, points[1:]):
            steps = max(abs(x1 - x0), abs(y1 - y0), 1)
            for s in range(steps + 1):
                x = x0 + (x1 - x0) * s // steps
                y = y0 + (y1 - y0) * s // steps
                self.px(x, y, idx)

    def specks(self, idx, coords):
        for x, y in coords:
            self.px(x, y, idx)


def alien_hull():
    """Обшивка корабля: тёмный металл, светящиеся швы и заклёпки."""
    c = Canvas("alien_hull")
    c.noise([c.color(22, 24, 26), c.color(30, 33, 36), c.color(16, 18, 20)], 3)
    c.frame(c.color(10, 12, 14))
    # панели 8x8 со светящимися швами
    c.hline(0, 15, 7, c.color(44, 92, 82))
    c.hline(0, 15, 8, c.color(28, 62, 56))
    c.vline(0, 15, 7, c.color(38, 80, 72))
    c.vline(0, 15, 8, c.color(26, 58, 52))
    # светящиеся вставки
    c.rect(2, 2, 3, 1, c.color(120, 226, 190))
    c.rect(11, 12, 3, 1, c.color(120, 226, 190))
    c.specks(c.color(190, 246, 226), [(2, 2), (3, 2), (11, 12), (12, 12)])
    # заклёпки
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14), (7, 1), (7, 14), (1, 7), (14, 7)):
        c.px(x, y, c.color(96, 104, 110))
    return c


def alien_alloy():
    """Сплав пришельцев: фиолетовые панели с техно-рисунком."""
    c = Canvas("alien_alloy")
    c.noise([c.color(44, 28, 66), c.color(56, 36, 82), c.color(34, 22, 52)], 13)
    c.frame(c.color(22, 14, 34))
    # техно-полосы
    c.rect(3, 3, 10, 1, c.color(122, 84, 190))
    c.rect(3, 12, 10, 1, c.color(122, 84, 190))
    c.rect(3, 4, 1, 8, c.color(88, 58, 148))
    c.rect(12, 4, 1, 8, c.color(88, 58, 148))
    # центральный реактор
    c.diamond(8, 8, 3, c.color(96, 60, 156))
    c.diamond(8, 8, 2, c.color(168, 128, 246))
    c.px(8, 8, c.color(232, 214, 255))
    c.specks(c.color(60, 38, 96), [(1, 1), (14, 1), (1, 14), (14, 14), (7, 1), (8, 14)])
    return c


def alien_core():
    """Energy-ядро: тёмный камень + светящаяся фиолетовая руна."""
    c = Canvas("alien_core")
    c.noise([c.color(26, 20, 34), c.color(34, 26, 44), c.color(20, 15, 27)], 29)
    c.frame(c.color(12, 9, 16))
    c.diamond(8, 8, 5, c.color(56, 26, 92))
    c.diamond(8, 8, 4, c.color(96, 40, 152))
    c.diamond(8, 8, 3, c.color(150, 82, 226))
    c.diamond(8, 8, 1, c.color(226, 196, 255))
    # лучи
    c.cracks(c.color(186, 128, 255), [(8, 3), (8, 8), (8, 13)])
    c.cracks(c.color(120, 70, 190), [(3, 8), (8, 8), (13, 8)])
    c.specks(c.color(74, 40, 120), [(2, 2), (13, 2), (2, 13), (13, 13)])
    return c


def alien_bio():
    """Био-масса: органические блоки с прожилами и порами."""
    c = Canvas("alien_bio")
    c.noise([c.color(40, 44, 40), c.color(52, 58, 50), c.color(32, 36, 34)], 41)
    c.frame(c.color(22, 26, 24))
    # «кирпичи» био-массы
    c.hline(0, 15, 5, c.color(24, 28, 26))
    c.hline(0, 15, 11, c.color(24, 28, 26))
    c.vline(0, 5, 7, c.color(24, 28, 26))
    c.vline(6, 11, 3, c.color(24, 28, 26))
    c.vline(12, 15, 11, c.color(24, 28, 26))
    # светящиеся прожилки
    c.cracks(c.color(96, 214, 150), [(2, 2), (6, 4), (4, 9), (9, 8), (13, 12)])
    c.cracks(c.color(150, 240, 190), [(2, 2), (5, 4), (4, 8)])
    # поры
    c.specks(c.color(20, 24, 22), [(2, 13), (9, 3), (13, 6), (6, 14)])
    return c


def make_block_textures():
    os.makedirs(OUT_BLOCKS, exist_ok=True)
    total = 0
    for canvas in (alien_hull(), alien_alloy(), alien_core(), alien_bio()):
        path = os.path.join(OUT_BLOCKS, canvas.name + ".png")
        size = write_png_palette(path, SIZE, canvas.palette, canvas.pixels)
        total += size
        print("%-24s %4d bytes, %d colors" % (canvas.name + ".png", size, len(canvas.palette)))
    print("итого блоки: %d bytes" % total)


def main():
    print("== текстуры мобов (перекраска ванильных) ==")
    make_entity_textures()
    print("== текстуры блоков ==")
    make_block_textures()


if __name__ == "__main__":
    main()
