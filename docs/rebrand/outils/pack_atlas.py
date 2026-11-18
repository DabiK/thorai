"""Cut generated sprite sheets onto the Aube palette and pack the game atlases.

Pillow only (no numpy).

usage:
  pack_atlas.py cut <raw.png> <factor> <preview.png> [merge] [satellite]
      list the sprites found on a magenta sheet (reading order) and save an
      upscaled preview with their index, to write a recipe.
  pack_atlas.py build <recipe.json> <rawdir> <outdir> [atlas ...]
      build <outdir>/<atlas>.png + <atlas>.json for each atlas of the recipe.

Recipe (see atlas-recette.json):
  palette        32 hex colours; every opaque pixel ends up on it (or on roofKeys)
  roofKeys       3 hex colours, light -> dark, painted on roofs (swapped at runtime)
  atlases.<name>.sources.<id>:
      file       raw sheet in <rawdir> (magenta #FF00FF background), or
      native     an already native RGBA png in <rawdir> (hand-drawn sprites, backgrounds), cut
                 with "grid": [cellW, cellH] ("trim": true crops each cell) or used whole
                 ("anchor": [ax, ay], default bottom centre)
      fit        {"index": i, "height": h}: scale so sprite i is h pixels tall
      factor     raw pixels per native pixel (instead of fit)
      merge      merge components closer than this many raw pixels (default 24)
      satellite  components smaller than this (in 4x4 cells) join the nearest big one
      roof       true (or a list of frame names): cobalt-blue roof pixels become the
                 three roofKeys, split light / base / shadow by luminance tertiles
      cell       [w, h]: fixed frame, feet anchored at (w/2, h-1); else tight box
      align      head | feet | box: horizontal reference for cell frames
      names      frame names in reading order (null skips a sprite)
  atlases.<name>.mirror   {"new": "existing"}: horizontal flip of a frame
  atlases.<name>.anims    {"anim": {"frames": [...], "ms": n}}
  atlases.<name>.objects  copied as is (footprint, door, chimney, windows ...)
  atlases.<name>.extra    extra top-level keys copied as is (the scenes of the backgrounds)

Native sprite colours are chosen per output pixel as the most frequent palette
colour among the raw pixels it covers, the centre of the block weighing more
(PACK_CENTRE, default 0.6) and dark outline colours too (PACK_DARK, default 1.2),
so outlines and eyes survive the reduction. A pixel is transparent when more than
half of its raw pixels are magenta. Reduce sheets close to their own art-pixel
size (about 7 for the characters): a large non-integer reduction blurs faces.
"""
import colorsys
import json
import math
import os
import sys
from PIL import Image, ImageDraw


def hex2rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def is_key(p):
    r, g, b = p[0], p[1], p[2]
    return r - g > 70 and b - g > 70 and r > 120 and b > 120


def lum(c):
    return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]


class Palette:
    def __init__(self, hexes):
        self.cols = [hex2rgb(h) for h in hexes]
        self.cache = {}

    def nearest(self, p):
        p = p[:3]
        i = self.cache.get(p)
        if i is None:
            best = None
            for k, c in enumerate(self.cols):
                rm = (p[0] + c[0]) / 2
                dr, dg, db = p[0] - c[0], p[1] - c[1], p[2] - c[2]
                d = (2 + rm / 256) * dr * dr + 4 * dg * dg + (2 + (255 - rm) / 256) * db * db
                if best is None or d < best:
                    best, i = d, k
            self.cache[p] = i
        return i


# vote weights: dark outline colours, and how much the centre of a native pixel dominates (0..1)
DARK = float(os.environ.get("PACK_DARK", "1.2"))
CENTRE = float(os.environ.get("PACK_CENTRE", "0.6"))


def is_roof(p):
    h, s, v = colorsys.rgb_to_hsv(p[0] / 255, p[1] / 255, p[2] / 255)
    # cobalt roofs only: the navy outline (dark) and its purple fringe on magenta stay out
    return 0.53 <= h <= 0.65 and s >= 0.45 and v >= 0.50


# ---------------------------------------------------------------- components

def components(img, merge=24, cell=4, satellite=0):
    """Sprites of a magenta sheet: boxes [x0, y0, x1, y1] in raw pixels, reading order."""
    w, h = img.size
    px = img.load()
    gw, gh = w // cell, h // cell
    occ = [[False] * gw for _ in range(gh)]
    for gy in range(gh):
        for gx in range(gw):
            n = 0
            for yy in range(gy * cell, gy * cell + cell):
                for xx in range(gx * cell, gx * cell + cell):
                    if not is_key(px[xx, yy]):
                        n += 1
            occ[gy][gx] = n * 2 >= cell * cell
    seen = [[False] * gw for _ in range(gh)]
    boxes = []
    for gy in range(gh):
        for gx in range(gw):
            if occ[gy][gx] and not seen[gy][gx]:
                st = [(gx, gy)]
                seen[gy][gx] = True
                x0 = x1 = gx
                y0 = y1 = gy
                c = 0
                while st:
                    cx, cy = st.pop()
                    c += 1
                    x0, x1, y0, y1 = min(x0, cx), max(x1, cx), min(y0, cy), max(y1, cy)
                    for dx in (-1, 0, 1):
                        for dy in (-1, 0, 1):
                            nx, ny = cx + dx, cy + dy
                            if 0 <= nx < gw and 0 <= ny < gh and occ[ny][nx] and not seen[ny][nx]:
                                seen[ny][nx] = True
                                st.append((nx, ny))
                if c >= 3:
                    boxes.append([x0 * cell, y0 * cell, (x1 + 1) * cell, (y1 + 1) * cell, c])
    # merge satellites (sparkles, smoke) into their neighbour; "core" keeps the biggest part
    boxes = [{"box": b[:4], "core": b[:4], "size": b[4], "count": b[4]} for b in boxes]
    if satellite:
        big = [b for b in boxes if b["size"] >= satellite]
        for b in [b for b in boxes if b["size"] < satellite]:
            def gap(o):
                A, B = b["box"], o["box"]
                return max(B[0] - A[2], A[0] - B[2], 0) + max(B[1] - A[3], A[1] - B[3], 0)
            host = min(big, key=gap)
            H = host["box"]
            host["box"] = [min(H[0], b["box"][0]), min(H[1], b["box"][1]), max(H[2], b["box"][2]), max(H[3], b["box"][3])]
            host["count"] += b["count"]
        boxes = big
    changed = True
    while changed:
        changed = False
        for i in range(len(boxes)):
            for j in range(i + 1, len(boxes)):
                A, B = boxes[i]["box"], boxes[j]["box"]
                if A[0] - merge <= B[2] and B[0] - merge <= A[2] and A[1] - merge <= B[3] and B[1] - merge <= A[3]:
                    main = boxes[i] if boxes[i]["size"] >= boxes[j]["size"] else boxes[j]
                    boxes[i] = {"box": [min(A[0], B[0]), min(A[1], B[1]), max(A[2], B[2]), max(A[3], B[3])],
                                "core": main["core"], "size": main["size"],
                                "count": boxes[i]["count"] + boxes[j]["count"]}
                    boxes.pop(j)
                    changed = True
                    break
            if changed:
                break
    out = boxes
    # reading order: rows clustered by vertical centre
    if not out:
        return out
    hs = sorted(o["box"][3] - o["box"][1] for o in out)
    gap = hs[len(hs) // 2] * 0.5
    out.sort(key=lambda o: (o["box"][1] + o["box"][3]) / 2)
    rows, cur, ref = [], [], None
    for o in out:
        yc = (o["box"][1] + o["box"][3]) / 2
        if cur and abs(yc - ref) > gap:
            rows.append(cur)
            cur = []
        if not cur:
            ref = yc
        cur.append(o)
    rows.append(cur)
    res = []
    for r, row in enumerate(rows):
        base = max(o["box"][3] for o in row)
        for o in sorted(row, key=lambda o: o["box"][0]):
            o["row"] = r
            o["base"] = base
            res.append(o)
    return res


# ---------------------------------------------------------------- reduction

class VoteGrid:
    """2D store of the winning vote of each native pixel (Pillow images cannot hold tuples)."""

    def __init__(self, w, h):
        self.size = (w, h)
        self.d = [[None] * w for _ in range(h)]

    def load(self):
        return self

    def __getitem__(self, xy):
        return self.d[xy[1]][xy[0]]

    def __setitem__(self, xy, v):
        self.d[xy[1]][xy[0]] = v


def reduce_sprite(img, comp, f, pal, roof_tiers=None, cell=None, align="head", baseline="row"):
    """Native RGBA sprite (palette indices kept in a parallel list) from a raw component."""
    px = img.load()
    W0, H0 = img.size
    x0, y0, x1, y1 = comp["box"]
    cx0, cy0, cx1, cy1 = comp["core"]
    bottom = comp["base"] if baseline == "row" else y1
    # horizontal reference
    if align == "box":
        ref = (x0 + x1) / 2
    else:
        hgt = cy1 - cy0
        if align == "head":
            ya, yb = cy0, cy0 + hgt * 0.28
        else:
            ya, yb = cy1 - hgt * 0.12, cy1
        sx = n = 0
        for yy in range(int(ya), int(yb)):
            for xx in range(cx0, cx1):
                if not is_key(px[xx, yy]):
                    sx += xx
                    n += 1
        ref = sx / n if n else (cx0 + cx1) / 2
    if cell:
        w, h = cell
        ax, ay = w // 2, h - 1
    else:
        h = math.ceil((bottom - y0) / f - 0.25)
        left = math.ceil((ref - x0) / f - 0.25)
        right = math.ceil((x1 - ref) / f - 0.25)
        w = left + right
        ax, ay = left, h - 1
    out = VoteGrid(w, h)
    op = out.load()
    for j in range(h):
        ry0 = bottom + (j - h) * f
        ry1 = ry0 + f
        for i in range(w):
            rx0 = ref + (i - ax) * f
            rx1 = rx0 + f
            a0, a1 = max(int(round(rx0)), 0), min(int(round(rx1)), W0)
            b0, b1 = max(int(round(ry0)), 0), min(int(round(ry1)), H0)
            if a1 <= a0 or b1 <= b0:
                continue
            votes = {}
            keys = tot = 0
            mx, my = (rx0 + rx1) / 2, (ry0 + ry1) / 2
            for yy in range(b0, b1):
                for xx in range(a0, a1):
                    # tent weight: the centre of the native pixel counts most
                    cw = (1.0 - CENTRE * abs(xx + 0.5 - mx) * 2 / f) * (1.0 - CENTRE * abs(yy + 0.5 - my) * 2 / f)
                    tot += 1
                    if not (x0 - 2 <= xx < x1 + 2 and y0 - 2 <= yy < y1 + 2):
                        keys += 1
                        continue
                    p = px[xx, yy]
                    if is_key(p):
                        keys += 1
                        continue
                    if roof_tiers and is_roof(p):
                        L = lum(p)
                        k = ("roof", 0 if L >= roof_tiers[0] else 1 if L >= roof_tiers[1] else 2)
                        wgt = 1.0
                    else:
                        idx = pal.nearest(p)
                        k = ("pal", idx)
                        wgt = DARK if lum(pal.cols[idx]) < 70 else 1.0
                    votes[k] = votes.get(k, 0) + wgt * cw
            if keys * 2 > tot or not votes:
                continue
            k = max(votes, key=votes.get)
            op[i, j] = k
    return out, ax, ay


def roof_thresholds(img, comps):
    px = img.load()
    ls = []
    for c in comps:
        x0, y0, x1, y1 = c["box"]
        for yy in range(y0, y1, 2):
            for xx in range(x0, x1, 2):
                p = px[xx, yy]
                if not is_key(p) and is_roof(p):
                    ls.append(lum(p))
    ls.sort()
    if not ls:
        return None
    return (ls[len(ls) * 2 // 3], ls[len(ls) // 3])


def paint(sprite, pal, keys):
    """Turn the vote keys stored in pixels into colours."""
    w, h = sprite.size
    sp = sprite.load()
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    ip = img.load()
    for y in range(h):
        for x in range(w):
            k = sp[x, y]
            if k:
                c = pal.cols[k[1]] if k[0] == "pal" else keys[k[1]]
                ip[x, y] = (c[0], c[1], c[2], 255)
    return img


def reduce_to_rgba(img, comp, f, pal, keys, roof_tiers=None, cell=None, align="head", baseline="row"):
    votes, ax, ay = reduce_sprite(img, comp, f, pal, roof_tiers, cell, align, baseline)
    return paint(votes, pal, keys), ax, ay


# ---------------------------------------------------------------- packing

def pack(frames, width=512, pad=1):
    """Shelf packing, tallest first. frames: {name: img}. Returns positions and atlas size."""
    order = sorted(frames, key=lambda n: (-frames[n].size[1], -frames[n].size[0], n))
    pos = {}
    x = y = shelf = 0
    for n in order:
        w, h = frames[n].size
        if x + w + pad > width:
            x = 0
            y += shelf + pad
            shelf = 0
        pos[n] = (x + pad, y + pad)
        x += w + pad
        shelf = max(shelf, h)
    H = y + shelf + 2 * pad
    return pos, (width, H)


def build_atlas(name, spec, recipe, rawdir, outdir):
    pal = Palette(recipe["palette"])
    keys = [hex2rgb(k) for k in recipe["roofKeys"]]
    frames, anchors = {}, {}
    for sid, src in spec["sources"].items():
        names = src.get("names", [])
        if "native" in src:
            im = Image.open(os.path.join(rawdir, src["native"])).convert("RGBA")
            if "grid" in src:
                gw, gh = src["grid"]
                k = 0
                for gy in range(0, im.size[1], gh):
                    for gx in range(0, im.size[0], gw):
                        if k < len(names) and names[k]:
                            fr = im.crop((gx, gy, gx + gw, gy + gh))
                            ax, ay = gw // 2, gh - 1
                            if src.get("trim"):
                                fr, ax, ay = trim(fr, ax, ay)
                                ax = fr.size[0] // 2
                            frames[names[k]] = fr
                            anchors[names[k]] = (ax, ay)
                        k += 1
            else:
                frames[names[0]] = im
                anchors[names[0]] = tuple(src.get("anchor", (im.size[0] // 2, im.size[1] - 1)))
            continue
        img = Image.open(os.path.join(rawdir, src["file"])).convert("RGB")
        comps = components(img, src.get("merge", 24), satellite=src.get("satellite", 0))
        if "fit" in src:
            c = comps[src["fit"]["index"]]
            f = (c["base"] - c["box"][1]) / src["fit"]["height"]
        else:
            f = src["factor"]
        tiers = roof_thresholds(img, comps) if src.get("roof") else None
        print("  %s: %d sprites, factor %.2f%s" % (sid, len(comps), f, ", roof" if tiers else ""))
        if len(names) != len(comps):
            print("  WARNING %s: %d names for %d sprites" % (sid, len(names), len(comps)))
        for k, c in enumerate(comps):
            if k >= len(names) or not names[k]:
                continue
            roof = src.get("roof")
            use = tiers if (roof is True or (isinstance(roof, list) and names[k] in roof)) else None
            fr, ax, ay = reduce_to_rgba(img, c, f, pal, keys, use, src.get("cell"),
                                        src.get("align", "head"), src.get("baseline", "row"))
            if not src.get("cell"):
                fr, ax, ay = trim(fr, ax, ay)
            frames[names[k]] = fr
            anchors[names[k]] = (ax, ay)
    for new, old in spec.get("mirror", {}).items():
        fr = frames[old].transpose(Image.Transpose.FLIP_LEFT_RIGHT)
        ax, ay = anchors[old]
        frames[new] = fr
        anchors[new] = (fr.size[0] - ax, ay)
    pos, size = pack(frames, spec.get("width", 512))
    atlas = Image.new("RGBA", size, (0, 0, 0, 0))
    meta = {"pixel": 1, "frames": {}, "anims": spec.get("anims", {}), "objects": spec.get("objects", {}),
            "extra": spec.get("extra", {})}
    for n in sorted(frames):
        x, y = pos[n]
        atlas.paste(frames[n], (x, y))
        w, h = frames[n].size
        meta["frames"][n] = {"x": x, "y": y, "w": w, "h": h, "ax": anchors[n][0], "ay": anchors[n][1]}
    check(meta, atlas, recipe)
    atlas.save(os.path.join(outdir, name + ".png"), optimize=True)
    with open(os.path.join(outdir, name + ".json"), "w") as fh:
        fh.write(dump_meta(meta))
    print("%s.png %dx%d, %d frames, %d anims, %d objects" % (
        name, size[0], size[1], len(meta["frames"]), len(meta["anims"]), len(meta["objects"])))


def dump_meta(meta):
    """JSON with one frame, anim or object per line, sorted, so that diffs stay readable."""
    def one(v):
        return json.dumps(v, ensure_ascii=False, separators=(", ", ": "))

    parts = ['{\n "pixel": %d' % meta["pixel"]]
    for key in ("frames", "anims", "objects"):
        items = ["  %s: %s" % (one(k), one(meta[key][k])) for k in sorted(meta[key])]
        parts.append(' "%s": {%s}' % (key, ("\n" + ",\n".join(items) + "\n ") if items else ""))
    for key, value in meta.get("extra", {}).items():
        items = ["  %s: %s" % (one(k), one(value[k])) for k in sorted(value)]
        parts.append(' "%s": {\n%s\n }' % (key, ",\n".join(items)))
    return ",\n".join(parts) + "\n}\n"


def trim(fr, ax, ay):
    bb = fr.getbbox()
    if not bb:
        return fr, ax, ay
    x0, y0, x1, y1 = bb
    return fr.crop((x0, y0, x1, fr.size[1])), ax - x0, ay - y0


def check(meta, atlas, recipe):
    allowed = {hex2rgb(h) for h in recipe["palette"]} | {hex2rgb(h) for h in recipe["roofKeys"]}
    bad = {p[:3] for p in atlas.get_flattened_data() if p[3] and p[:3] not in allowed}
    if bad:
        raise SystemExit("colours outside palette: %s" % sorted(bad)[:5])
    if any(p[3] not in (0, 255) for p in atlas.get_flattened_data()):
        raise SystemExit("semi-transparent pixels")
    for a, spec in meta["anims"].items():
        for f in spec["frames"]:
            if f not in meta["frames"]:
                raise SystemExit("anim %s: unknown frame %s" % (a, f))
    for n, f in meta["frames"].items():
        if not (0 <= f["ax"] <= f["w"] and 0 <= f["ay"] < f["h"]):
            raise SystemExit("frame %s: anchor outside the image" % n)
    for o, spec in meta["objects"].items():
        if spec["frame"] not in meta["frames"]:
            raise SystemExit("object %s: unknown frame %s" % (o, spec["frame"]))
        fp = spec["footprint"]
        door = spec.get("door")
        # rules of the village engine (HouseModel): door on the last row, house footprint <= 7 x 5
        if door and not (0 <= door["tx"] < fp["w"] and door["ty"] == fp["h"] - 1):
            raise SystemExit("object %s: the door must be on the last row of the footprint" % o)
        if o.startswith("maison.") and (fp["w"] > 7 or fp["h"] > 5):
            raise SystemExit("object %s: footprint above 7 x 5 tiles" % o)


# ---------------------------------------------------------------- preview

def cut_preview(raw, f, out, merge=24, satellite=0):
    img = Image.open(raw).convert("RGB")
    comps = components(img, merge, satellite=satellite)
    prev = img.copy()
    d = ImageDraw.Draw(prev)
    for k, c in enumerate(comps):
        x0, y0, x1, y1 = c["box"]
        d.rectangle((x0, y0, x1, y1), outline=(0, 255, 0))
        d.text((x0 + 2, y0 + 2), str(k), fill=(0, 0, 0))
        print(k, "row", c["row"], c["box"], "native %dx%d" % ((x1 - x0) / f, (c["base"] - y0) / f))
    prev.save(out)


if __name__ == "__main__":
    if sys.argv[1] == "cut":
        cut_preview(sys.argv[2], float(sys.argv[3]), sys.argv[4], int(sys.argv[5]) if len(sys.argv) > 5 else 24,
                    int(sys.argv[6]) if len(sys.argv) > 6 else 0)
    else:
        recipe = json.load(open(sys.argv[2]))
        rawdir, outdir = sys.argv[3], sys.argv[4]
        os.makedirs(outdir, exist_ok=True)
        only = sys.argv[5:]
        for name, spec in recipe["atlases"].items():
            if not only or name in only:
                build_atlas(name, spec, recipe, rawdir, outdir)
