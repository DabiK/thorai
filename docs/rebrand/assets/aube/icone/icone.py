"""ThorAI launcher icon, pixel art on the native 54 x 54 grid of the 108 dp adaptive icon.

The subject is redrawn pixel by pixel after the retained Codex piste (icone-brute.png,
consigne icone-brute.prompt.txt), on the Aube palette. The background is a dawn sky in
pixel bands with the rising sun behind the house.

usage: python3 icone.py <repo root>
writes android/app/src/main/res/mipmap-<density>/ic_launcher_{background,house,monochrome}.png
(nearest neighbour, x2 mdpi ... x8 xxxhdpi), icone-54.png next to this script, and the
512 x 512 preview docs/images/jeu/icone.png.
"""
import math
import os
import sys
from PIL import Image

G = 54                      # art pixels across the 108 dp layer
DENSITIES = {"mdpi": 2, "hdpi": 3, "xhdpi": 4, "xxhdpi": 6, "xxxhdpi": 8}

# ---------------------------------------------------------------- subject (32 x 33)
C = {
    "O": "#222F36",  # outline
    "L": "#E5A669",  # roof light
    "R": "#DF724A",  # roof base
    "r": "#DB6633",  # roof tile course
    "S": "#9E5A37",  # roof shade
    "C": "#F1D8A6",  # plaster
    "c": "#D8AFA1",  # plaster shade
    "H": "#A16948",  # wood light
    "W": "#9E5A37",  # wood
    "w": "#615039",  # wood dark
    "Y": "#DDDC2C",  # glow
    "y": "#DCDA5F",  # glow light
    "G": "#A0A0A0",  # stone
    "g": "#605751",  # stone shade
    "P": "#F0DDD1",  # smoke, pale stone, sparkle core
    "E": "#71C645",  # grass
    "e": "#22944C",  # grass shade
    "A": "#A0D944",  # grass light
}
W, H = 32, 33
PY = 6            # roof peak row
EAVE = PY + 13    # last roof row
WALL0 = EAVE + 2  # first wall row

grid = [[None] * W for _ in range(H)]


def put(x, y, k):
    if 0 <= x < W and 0 <= y < H:
        grid[y][x] = k


def hline(x0, x1, y, k):
    for x in range(x0, x1 + 1):
        put(x, y, k)


def rect(x0, y0, x1, y1, k):
    for y in range(y0, y1 + 1):
        hline(x0, x1, y, k)


def stamp(x0, y0, rows):
    for j, row in enumerate(rows):
        for i, k in enumerate(row):
            if k != ".":
                put(x0 + i, y0 + j, k)


# ---- chimney, drawn first: the roof covers its foot
rect(7, PY - 2, 12, PY + 6, "O")
hline(8, 11, PY - 1, "P")
rect(8, PY, 11, PY + 6, "G")
for y in range(PY, PY + 7):
    put(11, y, "g")
hline(8, 10, PY + 2, "g")

# ---- roof: thick inverted V, lit left slope, shaded right slope
for y in range(PY, EAVE + 1):
    l, r = 14 - (y - PY), 17 + (y - PY)
    for x in range(l + 1, r):
        if x <= 15:
            k = "L" if (x == l + 1 or y == PY + 1) else "R"
        else:
            k = "R" if (x == r - 1 or y == PY + 1) else "S"
        put(x, y, k)
    put(l, y, "O"); put(r, y, "O")
hline(14, 17, PY, "O")
for y in (PY + 4, PY + 7, PY + 10):        # tile courses
    l, r = 14 - (y - PY), 17 + (y - PY)
    for x in range(l + 2, r - 1):
        if grid[y][x] == "R" and x <= 15:
            put(x, y, "r")
        elif grid[y][x] == "S":
            put(x, y, "w")
# eave: lit edge, then the underside in shadow
for x in range(1, 31):
    put(x, EAVE, "L" if x < 16 else "R")
put(0, EAVE, "O"); put(31, EAVE, "O")
hline(0, 31, EAVE + 1, "O")

# ---- front gable: plaster triangle under the ridge, round lit window
GA = PY + 7
hline(15, 16, GA - 1, "O")
for y in range(GA, EAVE + 1):
    il, ir = 15 - (y - GA), 16 + (y - GA)
    put(il - 1, y, "O"); put(ir + 1, y, "O")
    hline(il, ir, y, "C")
    put(ir, y, "c")
stamp(14, GA + 1, [".OO.",
                   "OyYO",
                   "OYYO",
                   ".OO."])
hline(7, 24, EAVE + 1, "w")   # beam under the gable
put(6, EAVE + 1, "O"); put(25, EAVE + 1, "O")

# ---- smoke: a round puff over the chimney
stamp(10, 0, [".OOOO.",
              "OPPPPO",
              "OPPPGO",
              ".OOOO."])
# ---- sparkle (four points), right of the ridge
stamp(23, 2, ["...O...",
              "..OYO..",
              ".OYPYO.",
              "OYPPPYO",
              ".OYPYO.",
              "..OYO..",
              "...O..."])

# ---- front wall
WB = H - 3                     # foundation row
rect(3, WALL0, 28, WB + 1, "O")
rect(4, WALL0, 27, WB - 1, "C")
hline(4, 27, WALL0, "c")       # shadow under the eaves
rect(26, WALL0, 27, WB - 1, "c")
for y in range(WALL0, WB):
    put(4, y, "H"); put(27, y, "W")
# window, two by two panes
wy = WALL0 + 1
rect(6, wy, 12, wy + 6, "O")
rect(7, wy + 1, 11, wy + 5, "Y")
hline(7, 11, wy + 3, "w")
for y in range(wy + 1, wy + 6):
    put(9, y, "w")
for (x, y) in ((7, wy + 1), (10, wy + 1), (7, wy + 4), (10, wy + 4)):
    put(x, y, "y")
hline(5, 13, wy + 7, "H"); put(5, wy + 7, "W"); put(13, wy + 7, "W")
# door
rect(15, wy, 22, WB + 1, "O")
rect(16, wy + 1, 21, WB, "W")
hline(17, 20, wy, "O"); put(16, wy + 1, "O"); put(21, wy + 1, "O")
for y in range(wy + 1, WB + 1):
    put(17, y, "H")
    if y > wy + 1:
        put(21, y, "w")
put(16, wy + 2, "H")
put(20, wy + 5, "Y")
# foundation stones
hline(4, 14, WB, "G"); hline(23, 27, WB, "G")
for x in (8, 12, 26):
    put(x, WB, "g")
hline(3, 28, WB + 1, "O")
# step in front of the door
rect(14, WB + 1, 23, WB + 2, "O")
hline(15, 22, WB + 1, "P")
# grass tufts
stamp(0, WB - 3, ["..O.O...",
                  ".OAOEO..",
                  "OAEEEEO.",
                  "OEEeEEeO",
                  ".OOOOOO."])
stamp(25, WB - 1, ["..O.O.",
                   ".OEOAO",
                   "OEeEEO",
                   ".OOOOO"])



def hx(h):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5)) + (255,)


def subject():
    img = Image.new("RGBA", (W, H))
    for y in range(H):
        for x in range(W):
            if grid[y][x]:
                img.putpixel((x, y), hx(C[grid[y][x]]))
    return img


# ---------------------------------------------------------------- background
def sky():
    bands = [(0, "#5A718C"), (12, "#718EA0"), (20, "#D8AFA1"), (28, "#E5A669")]
    im = Image.new("RGBA", (G, G))
    px = im.load()
    for y in range(G):
        c = [col for r, col in bands if y >= r][-1]
        for x in range(G):
            px[x, y] = hx(c)
    for r, col in bands[1:]:            # one dithered row above each band
        for x in range(0, G, 2):
            px[x, r - 1] = hx(col)
    cx, cy, R = 27, 27, 14              # the rising sun, behind the roof
    for y in range(G):
        for x in range(G):
            d = ((x - cx + .5) ** 2 + (y - cy + .5) ** 2) ** .5
            if d <= R:
                px[x, y] = hx("#F1D8A6") if d <= R - 2 else hx("#F0DDD1")
    for x in range(G):                  # hills
        back = 37 + round(2.2 * math.cos((x - 40) / 9.0))
        front = 42 + round(1.4 * math.cos((x - 12) / 7.0))
        for y in range(back, G):
            px[x, y] = hx("#8FB237")
        px[x, back] = hx("#A0D944") if x % 3 else hx("#8FB237")
        for y in range(front, G):
            px[x, y] = hx("#58A949" if y < front + 6 else "#22944C")
    return im


def layers():
    s = subject()
    fg = Image.new("RGBA", (G, G))
    fg.alpha_composite(s, ((G - W) // 2, (G - H) // 2 + 1))
    # monochrome (themed icons): the silhouette, with the panes, the door and the eave cut out
    holes = set()
    for y in range(wy + 1, wy + 6):
        for x in range(7, 12):
            if grid[y][x] in ("Y", "y"):
                holes.add((x, y))
    for y in range(GA + 2, GA + 4):
        holes.update({(15, y), (16, y)})
    for y in range(wy + 2, WB + 1):
        for x in range(17, 21):
            holes.add((x, y))
    for x in range(2, 30):
        holes.add((x, EAVE + 1))
    ox, oy = (G - W) // 2, (G - H) // 2 + 1
    mono = Image.new("RGBA", (G, G))
    for y in range(H):
        for x in range(W):
            if grid[y][x] and (x, y) not in holes:
                mono.putpixel((ox + x, oy + y), (255, 255, 255, 255))
    return sky(), fg, mono


def preview(comp):
    """512 x 512: the 72 dp a launcher shows (36 art pixels) x 14, stepped rounded corners."""
    v = comp.crop((9, 9, 45, 45))
    r = 8
    for y in range(36):
        for x in range(36):
            dx = max(r - x - .5, 0, x + .5 - (36 - r))
            dy = max(r - y - .5, 0, y + .5 - (36 - r))
            if dx * dx + dy * dy > r * r:
                v.putpixel((x, y), (0, 0, 0, 0))
    out = Image.new("RGBA", (512, 512))
    out.alpha_composite(v.resize((504, 504), Image.Resampling.NEAREST), (4, 4))
    return out


if __name__ == "__main__":
    root = sys.argv[1]
    bg, fg, mono = layers()
    res = os.path.join(root, "android/app/src/main/res")
    for name, f in DENSITIES.items():
        d = os.path.join(res, "mipmap-" + name)
        os.makedirs(d, exist_ok=True)
        for layer, img in (("background", bg), ("house", fg), ("monochrome", mono)):
            img.resize((G * f, G * f), Image.Resampling.NEAREST).save(
                os.path.join(d, "ic_launcher_%s.png" % layer), optimize=True)
    comp = bg.copy()
    comp.alpha_composite(fg)
    comp.save(os.path.join(os.path.dirname(os.path.abspath(__file__)), "icone-54.png"), optimize=True)
    preview(comp).save(os.path.join(root, "docs/images/jeu/icone.png"), optimize=True)
    print("ok")
