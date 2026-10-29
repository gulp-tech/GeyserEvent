# -*- coding: utf-8 -*-
# Генератор текстур блоков призыва (16x16 PNG, палитровые, без сторонних библиотек).
#
# Файлы кладутся в src/main/resources/resourcepack-vanilla/textures/blocks/geyserevent/
# и попадают в GeyserEvent-Pack.mcpack через .packtools/build_pack.py.
#
# Каждая текстура — 16x16, цветовой тип 3 (палитра) + фильтр 0, поэтому файл
# весит считанные сотни байт («супер оптимизировано», без потери качества 16px).
import io, os, struct, zlib

SIZE = 16
OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                   "src", "main", "resources", "resourcepack-vanilla",
                   "textures", "blocks", "geyserevent")


# ---------------------------------------------------------------- PNG writer
def _chunk(tag, data):
    return (struct.pack(">I", len(data)) + tag + data
            + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))


def write_png(path, palette, pixels):
    """palette: [(r,g,b), ...]; pixels: список из SIZE*SIZE индексов палитры."""
    raw = bytearray()
    for y in range(SIZE):
        raw.append(0)                                   # filter type 0 (None)
        raw.extend(pixels[y * SIZE:(y + 1) * SIZE])
    png = b"\x89PNG\r\n\x1a\n"
    png += _chunk(b"IHDR", struct.pack(">IIBBBBB", SIZE, SIZE, 8, 3, 0, 0, 0))
    png += _chunk(b"PLTE", b"".join(bytes(c) for c in palette))
    png += _chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    png += _chunk(b"IEND", b"")
    io.open(path, "wb").write(png)
    return len(png)


class Canvas(object):
    """Палитра + пиксели, с удобными примитивами."""

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
        """Детерминированный «шум»: каждый пиксель получает цвет из colors по хешу."""
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

    def rune(self, cx, cy, idx):
        """Крест-руна 5x5."""
        self.rect(cx - 2, cy, 5, 1, idx)
        self.rect(cx, cy - 2, 1, 5, idx)
        self.px(cx - 1, cy - 1, idx)
        self.px(cx + 1, cy - 1, idx)
        self.px(cx - 1, cy + 1, idx)
        self.px(cx + 1, cy + 1, idx)

    def cracks(self, idx, points):
        """Диагольные трещины: точки (x,y) соединяем линиями."""
        for (x0, y0), (x1, y1) in zip(points, points[1:]):
            steps = max(abs(x1 - x0), abs(y1 - y0), 1)
            for s in range(steps + 1):
                x = x0 + (x1 - x0) * s // steps
                y = y0 + (y1 - y0) * s // steps
                self.px(x, y, idx)

    def specks(self, idx, coords):
        for x, y in coords:
            self.px(x, y, idx)


# ---------------------------------------------------------------- текстуры
def summon_altar():
    """Алтарь призыва: тёмный камень + светящаяся красная руна по центру."""
    c = Canvas("summon_altar")
    base = [c.color(38, 36, 42), c.color(46, 43, 50), c.color(31, 29, 34)]
    c.noise(base, 7)
    c.frame(c.color(20, 18, 22))
    # руна: тёмно-красное гало → ярко-красное → золотое ядро
    c.diamond(8, 8, 4, c.color(96, 18, 8))
    c.diamond(8, 8, 3, c.color(168, 32, 12))
    c.diamond(8, 8, 2, c.color(232, 74, 20))
    c.rune(8, 8, c.color(255, 138, 46))
    c.px(8, 8, c.color(255, 226, 150))
    # «заклёпки» по углам
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        c.px(x, y, c.color(74, 66, 60))
    return c


def pylon_ochre():
    """Пилон 1 (охристый): чёрный камень + угольные трещины."""
    c = Canvas("summon_pylon_1")
    c.noise([c.color(24, 19, 15), c.color(34, 26, 19), c.color(17, 13, 11)], 11)
    c.frame(c.color(12, 9, 8))
    c.cracks(c.color(214, 138, 42), [(3, 2), (6, 6), (4, 10), (7, 13)])
    c.cracks(c.color(255, 196, 92), [(3, 2), (5, 5), (4, 9)])
    c.specks(c.color(120, 74, 22), [(11, 3), (12, 11), (9, 12), (2, 8)])
    return c


def pylon_verdant():
    """Пилон 2 (изумрудный): тёмный камень + зелёные прожилки."""
    c = Canvas("summon_pylon_2")
    c.noise([c.color(15, 24, 17), c.color(22, 34, 23), c.color(11, 18, 13)], 23)
    c.frame(c.color(8, 13, 9))
    c.cracks(c.color(58, 158, 74), [(2, 4), (5, 8), (3, 12), (8, 14)])
    c.cracks(c.color(140, 236, 150), [(2, 4), (4, 8), (3, 11)])
    c.specks(c.color(34, 96, 50), [(12, 4), (10, 10), (13, 13)])
    return c


def pylon_pearlescent():
    """Пилон 3 (жемчужный): синеватый камень + фиолетовые прожилки."""
    c = Canvas("summon_pylon_3")
    c.noise([c.color(26, 22, 38), c.color(36, 30, 52), c.color(19, 16, 28)], 31)
    c.frame(c.color(13, 11, 20))
    c.cracks(c.color(126, 84, 214), [(3, 3), (6, 7), (4, 11), (9, 13)])
    c.cracks(c.color(198, 168, 255), [(3, 3), (5, 6), (4, 10)])
    c.specks(c.color(74, 48, 132), [(11, 5), (12, 12), (8, 2)])
    return c


def pylon_amethyst():
    """Пилон 4 (аметист): кристаллическая глыба с сияющим центром."""
    c = Canvas("summon_pylon_4")
    c.noise([c.color(30, 20, 40), c.color(42, 28, 56), c.color(22, 15, 30)], 43)
    c.frame(c.color(15, 10, 21))
    c.diamond(8, 8, 5, c.color(84, 52, 128))
    c.diamond(8, 8, 3, c.color(146, 96, 210))
    c.diamond(8, 8, 1, c.color(224, 196, 255))
    c.specks(c.color(190, 150, 240), [(4, 5), (11, 6), (5, 11), (10, 11)])
    return c


def main():
    if not os.path.isdir(OUT):
        os.makedirs(OUT)
    total = 0
    for canvas in (summon_altar(), pylon_ochre(), pylon_verdant(),
                   pylon_pearlescent(), pylon_amethyst()):
        path = os.path.join(OUT, canvas.name + ".png")
        size = write_png(path, canvas.palette, canvas.pixels)
        total += size
        print("%-24s %4d bytes, %d colors" % (canvas.name + ".png", size, len(canvas.palette)))
    print("total: %d bytes" % total)


if __name__ == "__main__":
    main()
