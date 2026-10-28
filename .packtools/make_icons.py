#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Пишет три 16x16 PNG-иконки для панели ивентов GeyserEvent (без PIL, только zlib)."""
import os
import struct
import zlib

OUT = "/home/user/GeyserEvent/src/main/resources/resourcepack-vanilla/textures/geyserevent"

SKULL = [
    "................",
    "................",
    "....wwwwwwww....",
    "...wwwwwwwwww...",
    "..wwwwwwwwwwww..",
    "..wwwwwwwwwwww..",
    "..wwwbbwwwwbwww..".replace("wwwbbwwwwbwww", "wwwwbbwwbbwwww")[:16] + ".",
    "..wwwwbbwwbbwwww.".replace("wwww", "wwww")[:16] + ".",
    "..wwwwwwwwwwww..",
    "..wwwwwwwwwwww..",
    "...wwwwwwwwww...",
    "...wwwwbbbbww...",
    "....wwbbbbbbww..".replace("wwbbbbbbww", "wwwwwwwwww")[:16] + ".",
    ".....wwwwww.....",
    "......wwww......",
    "................",
]

# аккуратная черепная кость: череп + глазницы + зубы
SKULL = [
    "................",
    "................",
    ".....ssssss.....",
    "....ssssssss....",
    "...ssssssssss...",
    "...ssssssssss...",
    "...ssseesssees..".replace("ssseesssees", "ssseessseess")[:16] + ".",
    "...ssseesssees..",
    "...ssssssssss...",
    "...ssssssssss...",
    "....ssssssss....",
    "....sseesseess..".replace("sseesseess", "ssssssssss")[:16] + ".",
    ".....ssssss.....",
    "......ssss......",
    "................",
    "................",
]

# якорь
ANCHOR = [
    "................",
    ".......oo.......",
    ".......oo.......",
    ".......oo.......",
    ".....oooooo.....",
    "..oooooooooooo..",
    "..oooooooooooo..",
    ".......oo.......",
    ".......oo.......",
    ".......oo.......",
    ".......oo.......",
    ".....oooooo.....",
    "....oooooooo....",
    "...oooooooooo...",
    "..ooo......ooo..",
    "................",
]

# колба алхимика
FLASK = [
    "................",
    ".......ff.......",
    "......ffff......",
    "......ffff......",
    "......ffff......",
    ".....ff..ff.....",
    ".....ff..ff.....",
    "....ff....ff....",
    "....ff....ff....",
    "...ff......ff...",
    "...ff......ff...",
    "...ff...ff..ff..".replace("ff...ff", "ff...ff")[:16] + ".",
    "....ffffffff....",
    ".....ffffff.....",
    "................",
    "................",
]


def write_png(path, rows):
    """rows: список строк длиной 16, символы: s/e/f/o = цвет, w = белый, b = чёрный, . = пусто."""
    h = len(rows)
    w = len(rows[0])
    raw = bytearray()
    palette = {
        "s": (200, 200, 205, 255),   # кость
        "e": (18, 18, 22, 255),      # глазницы
        "o": (240, 176, 60, 255),    # золото якоря
        "f": (186, 118, 235, 255),   # пурпур колбы
        "b": (18, 18, 22, 255),
        "w": (245, 245, 248, 255),
        ".": (0, 0, 0, 0),
    }
    for row in rows:
        raw.append(0)  # filter type 0
        for ch in row:
            raw.extend(palette[ch])

    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    ihdr = struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)
    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b"")
    with open(path, "wb") as fh:
        fh.write(png)
    return len(png)


os.makedirs(OUT, exist_ok=True)
print("skull   ->", write_png(os.path.join(OUT, "icon_rift.png"), SKULL), "bytes")
print("anchor  ->", write_png(os.path.join(OUT, "icon_excavation.png"), ANCHOR), "bytes")
print("flask   ->", write_png(os.path.join(OUT, "icon_cauldron.png"), FLASK), "bytes")
