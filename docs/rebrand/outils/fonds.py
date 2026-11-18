"""Full-screen backgrounds of the game view (480 x 270 native), on the Aube palette.

usage: fonds.py <recipe.json> <rawdir> <outdir> <name>=<raw.png>:<factor>[:#surround] ...
  A raw scene is reduced by <factor> (default 5, the world scale where a character is 30 px
  tall), each native pixel taking the most frequent palette colour of its raw block (centre
  weighted), then centred on a 480 x 270 canvas filled with the deep surround colour #203648,
  or with the given one (the tower, whose round room already stands on teal).
  A <name>=<native.png> without factor (already 480 x 270 on the palette) is copied as is.
"""
import os
import sys
import json
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pack_atlas import Palette, hex2rgb  # noqa: E402

W, H = 480, 270
SURROUND = "#203648"


def reduce_scene(img, f, pal):
    w0, h0 = img.size
    w, h = int(w0 / f), int(h0 / f)
    px = img.load()
    out = Image.new("RGB", (w, h))
    op = out.load()
    for j in range(h):
        for i in range(w):
            x0, x1 = int(round(i * f)), int(round((i + 1) * f))
            y0, y1 = int(round(j * f)), int(round((j + 1) * f))
            mx, my = (x0 + x1) / 2, (y0 + y1) / 2
            votes = {}
            for yy in range(y0, min(y1, h0)):
                for xx in range(x0, min(x1, w0)):
                    k = pal.nearest(px[xx, yy])
                    cw = (1 - 0.6 * abs(xx + 0.5 - mx) * 2 / f) * (1 - 0.6 * abs(yy + 0.5 - my) * 2 / f)
                    votes[k] = votes.get(k, 0) + cw
            op[i, j] = pal.cols[max(votes, key=votes.get)]
    return out


def main():
    recipe = json.load(open(sys.argv[1]))
    rawdir, outdir = sys.argv[2], sys.argv[3]
    os.makedirs(outdir, exist_ok=True)
    pal = Palette(recipe["palette"])
    allowed = set(pal.cols)
    for arg in sys.argv[4:]:
        name, src = arg.split("=", 1)
        if ":" in src:
            src, f, *colour = src.split(":")
            room = reduce_scene(Image.open(os.path.join(rawdir, src)).convert("RGB"), float(f), pal)
            canvas = Image.new("RGB", (W, H), hex2rgb(colour[0] if colour else SURROUND))
            ox, oy = (W - room.size[0]) // 2, (H - room.size[1]) // 2
            canvas.paste(room, (ox, oy))
            print("%s: room %dx%d at (%d, %d)" % (name, room.size[0], room.size[1], ox, oy))
        else:
            canvas = Image.open(os.path.join(rawdir, src)).convert("RGB")
            assert canvas.size == (W, H), src
        bad = {c for c in canvas.get_flattened_data() if c not in allowed}
        assert not bad, "%s: colours outside palette %s" % (name, sorted(bad)[:3])
        canvas.save(os.path.join(outdir, name + ".png"), optimize=True)


if __name__ == "__main__":
    main()
