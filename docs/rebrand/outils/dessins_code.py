"""Hand-drawn native sprites that must tile exactly: fence pieces and small UI glyphs.

usage: dessins_code.py <outdir>
  -> <outdir>/clotures.png : 16 fence pieces, cells of 16 x 24, in the order of
     the neighbour mask n=1 e=2 s=4 w=8 (0 = lone post ... 15 = cross).
     The tile is the bottom 16 rows of the cell; the post stands on it.
  -> <outdir>/glyphes.png  : UI glyphs in cells of 16 x 16 (see GLYPHS order).
Only Aube palette colours are used.
"""
import os
import sys
from PIL import Image

INK = (0x22, 0x2F, 0x36)      # outline
CAP = (0xF1, 0xD8, 0xA6)      # post top, light paper
LIGHT = (0xE5, 0xA6, 0x69)    # wood light
MID = (0xA1, 0x69, 0x48)      # wood mid
DARK = (0x9E, 0x5A, 0x37)     # wood shade
DEEP = (0x61, 0x50, 0x39)     # wood underside
PAPER = (0xF0, 0xDD, 0xD1)
CORAL = (0xDB, 0x66, 0x33)
SKY = (0x71, 0x8E, 0xA0)
GREEN = (0x1F, 0x58, 0x3A)
TEAL = (0x1B, 0x5F, 0x53)


def put(img, x, y, c):
    if 0 <= x < img.size[0] and 0 <= y < img.size[1]:
        img.putpixel((x, y), c + (255,))


def hrail(img, x0, x1, y):
    for x in range(x0, x1 + 1):
        put(img, x, y, INK)
        put(img, x, y + 1, LIGHT)
        put(img, x, y + 2, MID)
        put(img, x, y + 3, INK)


def vrail(img, y0, y1, x):
    for y in range(y0, y1 + 1):
        put(img, x, y, INK)
        put(img, x + 1, y, LIGHT)
        put(img, x + 2, y, MID)
        put(img, x + 3, y, INK)


def post(img, ox, oy):
    """Round log post, 6 x 14, top-left at (ox, oy)."""
    rows = [
        " kkkk ",
        "kccccl",
        "kllccl",
        "klmmmk",
        "klmmdk",
        "klmmdk",
        "klmmdk",
        "klmmdk",
        "klmmdk",
        "klmmdk",
        "klmmdk",
        "kmmddk",
        "kmeeek",
        " kkkk ",
    ]
    cols = {"k": INK, "c": CAP, "l": LIGHT, "m": MID, "d": DARK, "e": DEEP}
    for j, r in enumerate(rows):
        for i, ch in enumerate(r):
            if ch in cols:
                put(img, ox + i, oy + j, cols[ch])


def fence(mask):
    img = Image.new("RGBA", (16, 24), (0, 0, 0, 0))
    # vertical rails first (behind), then horizontal ones, then the post
    if mask & 1:
        vrail(img, 4, 12, 6)  # the post of the tile above ends at y 3
    if mask & 4:
        vrail(img, 12, 23, 6)
    if mask & 8:
        hrail(img, 0, 6, 11)
    if mask & 2:
        hrail(img, 9, 15, 11)
    post(img, 5, 6)
    return img


def outline(img, colour=INK):
    """Ink every transparent pixel touching a drawn one (8 neighbours)."""
    w, h = img.size
    src = img.copy()
    for y in range(h):
        for x in range(w):
            if src.getpixel((x, y))[3]:
                continue
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < w and 0 <= ny < h and src.getpixel((nx, ny))[3]:
                        put(img, x, y, colour)
                        break
                else:
                    continue
                break


def bubble(content):
    """Speech bubble 13 x 9 with a tail, on a 16 x 16 cell, bottom aligned."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    w, h, oy = 13, 9, 3
    for y in range(h):
        for x in range(w):
            corner = (x in (0, w - 1)) and (y in (0, h - 1))
            edge = x in (0, w - 1) or y in (0, h - 1)
            if corner:
                continue
            put(img, x + 1, y + oy, INK if edge else PAPER)
    # corners rounded by one pixel inside
    for x, y in ((1, 1), (w - 2, 1), (1, h - 2), (w - 2, h - 2)):
        put(img, x + 1, y + oy, INK)
    # tail
    for x, y, c in ((4, 9, PAPER), (3, 9, INK), (5, 9, INK), (4, 10, PAPER), (3, 10, INK), (5, 10, INK),
                    (4, 11, INK), (3, 11, INK)):
        put(img, x, y + oy - 1 + 1, c)
    cx = 1 + w // 2
    if content == "!":
        for y in range(oy + 2, oy + 5):
            put(img, cx, y, CORAL)
        put(img, cx, oy + 6, CORAL)
    else:
        for k in range(content):
            put(img, cx - 3 + 3 * k, oy + 4, INK)
    return img


def zed(img, x0, y0, n):
    for i in range(n):
        put(img, x0 + i, y0, PAPER)
        put(img, x0 + i, y0 + n - 1, PAPER)
        put(img, x0 + n - 1 - i, y0 + i, PAPER)


def sleep_glyph(step):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    if step == 0:
        zed(img, 1, 10, 4)
        zed(img, 8, 3, 5)
    else:
        zed(img, 2, 8, 4)
        zed(img, 9, 1, 5)
    outline(img)
    return img


def cursor_glyph():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for i in range(4):
        for j in range(i, 7 - i):
            put(img, 6 + i, 4 + j, PAPER)
    outline(img)
    return img


def arrow_down_glyph():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for i in range(4):
        for x in range(4 + i, 11 - i):
            put(img, x, 8 + i, PAPER)
    outline(img)
    return img


GLYPHS = {
    "bulle.attente": lambda: bubble("!"),
    "bulle.travail.0": lambda: bubble(1),
    "bulle.travail.1": lambda: bubble(2),
    "bulle.travail.2": lambda: bubble(3),
    "sommeil.0": lambda: sleep_glyph(0),
    "sommeil.1": lambda: sleep_glyph(1),
    "curseur": cursor_glyph,
    "fleche.bas": arrow_down_glyph,
}

# 3 x 5 letters for the gamepad buttons
FONT = {
    "A": ["010", "101", "111", "101", "101"],
    "B": ["110", "101", "110", "101", "110"],
    "X": ["101", "101", "010", "101", "101"],
    "Y": ["101", "101", "010", "010", "010"],
    "L": ["100", "100", "100", "100", "111"],
    "R": ["110", "101", "110", "101", "101"],
    "1": ["010", "110", "010", "010", "111"],
    "2": ["110", "001", "010", "100", "111"],
}


def octagon_button(letter):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    shape = [
        "   kkkkkk   ",
        "  kppppppk  ",
        " kppppppppk ",
        "kppppppppppk",
        "kppppppppppk",
        "kppppppppppk",
        "kppppppppppk",
        "kppppppppppk",
        "kppppppppppk",
        " kppppppppk ",
        "  kmmmmmmk  ",
        "   kkkkkk   ",
    ]
    draw_rows(img, shape, 2, 2)
    draw_text(img, letter, 2 + 5 - 1 + 1, 2 + 3)
    return img


def rect_button(text):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    w = 4 * len(text) + 3
    rows = [" " + "k" * (w - 2) + " "] + ["k" + "p" * (w - 2) + "k"] * 6 + \
        ["k" + "m" * (w - 2) + "k", " " + "k" * (w - 2) + " "]
    ox = (16 - w) // 2
    draw_rows(img, rows, ox, 4)
    draw_text(img, text, ox + 2, 5)
    return img


def draw_rows(img, rows, ox, oy):
    cols = {"k": INK, "p": CAP, "c": CORAL, "m": LIGHT, "s": SKY}
    for j, r in enumerate(rows):
        for i, ch in enumerate(r):
            if ch in cols:
                put(img, ox + i, oy + j, cols[ch])


def draw_text(img, text, ox, oy):
    x = ox
    for ch in text:
        for j, r in enumerate(FONT[ch]):
            for i, b in enumerate(r):
                if b == "1":
                    put(img, x + i, oy + j, INK)
        x += 4


def glyph_sheet():
    names = list(GLYPHS) + ["touche.a", "touche.b", "touche.x", "touche.y",
                            "touche.l1", "touche.r1", "touche.l2", "touche.r2"]
    sheet = Image.new("RGBA", (16 * len(names), 16), (0, 0, 0, 0))
    for k, n in enumerate(names):
        if n in GLYPHS:
            g = GLYPHS[n]()
        elif n.startswith("touche.") and len(n) == 8:
            g = octagon_button(n[-1].upper())
        else:
            g = rect_button(n[7:].upper())
        sheet.paste(g, (16 * k, 0))
    return sheet, names


if __name__ == "__main__":
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    sheet = Image.new("RGBA", (16 * 16, 24), (0, 0, 0, 0))
    for m in range(16):
        sheet.paste(fence(m), (16 * m, 0))
    sheet.save(os.path.join(out, "clotures.png"))
    g, names = glyph_sheet()
    g.save(os.path.join(out, "glyphes.png"))
    print("clotures.png 16 pieces; glyphes.png:", " ".join(names))
