"""Review contact sheet: every atlas upscaled x3 on a neutral background (backgrounds at half size).

usage: apercu_atlas.py <assets/game dir> <out.png>
"""
import json
import os
import sys
from PIL import Image, ImageDraw

S = 3
BG = (0xC8, 0xC4, 0xB4, 255)
CELL = (0xD6, 0xD2, 0xC2, 255)
INK = (0x22, 0x2F, 0x36, 255)


def atlas_block(path, name, width):
    """Frames grouped by name prefix, in rows, each on a light cell, anchor marked by a red dot."""
    meta = json.load(open(os.path.join(path, name + ".json")))
    at = Image.open(os.path.join(path, name + ".png")).convert("RGBA")
    groups = {}
    for n in sorted(meta["frames"]):
        g = n.rsplit(".", 1)[0] if n.rsplit(".", 1)[-1].isdigit() else n.split(".")[0]
        groups.setdefault(g, []).append(n)
    rows, cur, cw = [], [], 0
    for g in groups.values():
        gw = sum(meta["frames"][n]["w"] + 3 for n in g) + 6
        if cur and cw + gw > width:
            rows.append(cur)
            cur, cw = [], 0
        cur.append(g)
        cw += gw
    rows.append(cur)
    hs = [max(meta["frames"][n]["h"] for g in r for n in g) + 4 for r in rows]
    img = Image.new("RGBA", (width, sum(hs) + 14), BG)
    d = ImageDraw.Draw(img)
    d.text((2, 1), "%s : %d images, %d animations, %d objets" % (
        name, len(meta["frames"]), len(meta["anims"]), len(meta["objects"])), fill=INK)
    y = 14
    for r, h in zip(rows, hs):
        x = 2
        for g in r:
            for n in g:
                f = meta["frames"][n]
                fr = at.crop((f["x"], f["y"], f["x"] + f["w"], f["y"] + f["h"]))
                oy = y + h - 4 - f["h"]
                d.rectangle((x, oy, x + f["w"] - 1, oy + f["h"] - 1), fill=CELL)
                img.alpha_composite(fr, (x, oy))
                img.putpixel((x + min(f["ax"], f["w"] - 1), oy + f["ay"]), (255, 0, 0, 255))
                x += f["w"] + 3
            x += 6
        y += h
    return img


def main():
    path, out = sys.argv[1], sys.argv[2]
    W = 640
    blocks = [atlas_block(path, n, W) for n in ("personnages", "maisons", "decor", "icones")]
    meta = json.load(open(os.path.join(path, "fonds.json")))
    at = Image.open(os.path.join(path, "fonds.png")).convert("RGBA")
    names = sorted(meta["frames"])
    fb = Image.new("RGBA", (W, 14 + ((len(names) + 1) // 2) * 140), BG)
    ImageDraw.Draw(fb).text((2, 1), "fonds : %d images 480 x 270, ici reduites de moitie" % len(names), fill=INK)
    for k, n in enumerate(names):
        f = meta["frames"][n]
        im = at.crop((f["x"], f["y"], f["x"] + f["w"], f["y"] + f["h"])).resize((240, 135), Image.NEAREST)
        fb.alpha_composite(im, (2 + (k % 2) * 250, 14 + (k // 2) * 140))
    blocks.append(fb)
    H = sum(b.size[1] + 6 for b in blocks)
    sheet = Image.new("RGBA", (W, H), BG)
    y = 0
    for b in blocks:
        sheet.alpha_composite(b, (0, y))
        y += b.size[1] + 6
    sheet.resize((W * S, H * S), Image.NEAREST).convert("RGB").save(out, optimize=True)
    print(out, (W * S, H * S))


if __name__ == "__main__":
    main()
