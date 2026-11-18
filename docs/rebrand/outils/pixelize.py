"""Snap a generated image onto a true pixel grid.

usage: pixelize.py scene <raw.png> <out.png> <factor> <W> <H> <colors> [palette_from.png]
       pixelize.py sheet <raw.png> <outdir> <factor> <colors> [palette_from.png]

scene: downscale by <factor> (box filter), cover-crop to W x H, quantize to <colors>
       (or to the palette of palette_from.png), save native PNG + <out>.palette.json
sheet: same downscale, magenta background -> transparent, quantize, then cut each
       sprite (connected components) into <outdir>/sprite-NN.png + sheet.png
"""
import json
import sys
from PIL import Image


def lum(c):
    r, g, b = c[:3]
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def downscale(img, f):
    w, h = img.size
    return img.resize((max(1, round(w / f)), max(1, round(h / f))), Image.Resampling.BOX)


def cover_crop(img, W, H):
    w, h = img.size
    s = max(W / w, H / h)
    if s > 1:
        img = img.resize((round(w * s), round(h * s)), Image.Resampling.NEAREST)
        w, h = img.size
    x, y = (w - W) // 2, (h - H) // 2
    return img.crop((x, y, x + W, y + H))


def quantize(img, n, pal_src=None):
    rgb = img.convert("RGB")
    if pal_src:
        ref = Image.open(pal_src)
        q = rgb.quantize(palette=ref, dither=Image.Dither.NONE)
    else:
        q = rgb.quantize(colors=n, method=Image.Quantize.FASTOCTREE, dither=Image.Dither.NONE)
    return q


def palette_of(q):
    pal = q.getpalette()
    counts = q.getcolors(1 << 16) or []
    out = []
    for cnt, idx in counts:
        c = tuple(pal[idx * 3: idx * 3 + 3])
        out.append((c, cnt))
    out.sort(key=lambda t: lum(t[0]))
    return [{"hex": "#%02X%02X%02X" % c, "share": round(cnt / sum(x[1] for x in out), 4)} for c, cnt in out]


def scene(raw, out, f, W, H, n, pal_src=None):
    img = Image.open(raw).convert("RGB")
    small = cover_crop(downscale(img, f), W, H)
    q = quantize(small, n, pal_src)
    q.save(out, optimize=True)
    json.dump(palette_of(q), open(out.replace(".png", ".palette.json"), "w"), indent=1)
    print(out, q.size, len(palette_of(q)), "colors")


def is_key(p):
    r, g, b = p[:3]
    return r > 150 and b > 150 and g < 110 and abs(r - b) < 90


def sheet(raw, outdir, f, n, pal_src=None):
    import os
    os.makedirs(outdir, exist_ok=True)
    img = Image.open(raw).convert("RGB")
    full = img.load()
    W0, H0 = img.size
    fi = int(round(f))
    w, h = W0 // fi, H0 // fi
    small = Image.new("RGB", (w, h), (0, 0, 0))
    mask = Image.new("L", (w, h), 0)
    sp, mp = small.load(), mask.load()
    for y in range(h):
        for x in range(w):
            r = g = b = c = k = 0
            for yy in range(y * fi, y * fi + fi):
                for xx in range(x * fi, x * fi + fi):
                    px = full[xx, yy]
                    if is_key(px):
                        k += 1
                    else:
                        r += px[0]; g += px[1]; b += px[2]; c += 1
            if c and c >= k:
                sp[x, y] = (r // c, g // c, b // c)
                mp[x, y] = 255
    q = quantize(small, n, pal_src).convert("RGBA")
    q.putalpha(mask)
    q.save(os.path.join(outdir, "sheet.png"), optimize=True)
    # connected components on alpha
    a = mask.load()
    seen = [[False] * w for _ in range(h)]
    boxes = []
    for y in range(h):
        for x in range(w):
            if a[x, y] and not seen[y][x]:
                stack = [(x, y)]
                seen[y][x] = True
                x0 = x1 = x
                y0 = y1 = y
                cnt = 0
                while stack:
                    cx, cy = stack.pop()
                    cnt += 1
                    x0, x1, y0, y1 = min(x0, cx), max(x1, cx), min(y0, cy), max(y1, cy)
                    for dx in (-1, 0, 1):
                        for dy in (-1, 0, 1):
                            nx, ny = cx + dx, cy + dy
                            if 0 <= nx < w and 0 <= ny < h and a[nx, ny] and not seen[ny][nx]:
                                seen[ny][nx] = True
                                stack.append((nx, ny))
                if cnt >= 40:
                    boxes.append((x0, y0, x1 + 1, y1 + 1, cnt))
    # merge boxes that overlap/are near (sparkles etc.)
    merged = True
    while merged:
        merged = False
        for i in range(len(boxes)):
            for j in range(i + 1, len(boxes)):
                A, B = boxes[i], boxes[j]
                if A[0] - 3 <= B[2] and B[0] - 3 <= A[2] and A[1] - 3 <= B[3] and B[1] - 3 <= A[3]:
                    boxes[i] = (min(A[0], B[0]), min(A[1], B[1]), max(A[2], B[2]), max(A[3], B[3]), A[4] + B[4])
                    boxes.pop(j)
                    merged = True
                    break
            if merged:
                break
    boxes.sort(key=lambda b: (round(b[1] / 40), b[0]))
    meta = []
    for k, (x0, y0, x1, y1, cnt) in enumerate(boxes):
        p = os.path.join(outdir, "sprite-%02d.png" % k)
        q.crop((x0, y0, x1, y1)).save(p, optimize=True)
        meta.append({"file": os.path.basename(p), "x": x0, "y": y0, "w": x1 - x0, "h": y1 - y0})
    json.dump(meta, open(os.path.join(outdir, "sprites.json"), "w"), indent=1)
    print(outdir, (w, h), len(meta), "sprites")


if __name__ == "__main__":
    mode = sys.argv[1]
    if mode == "scene":
        _, _, raw, out, f, W, H, n, *rest = sys.argv
        scene(raw, out, float(f), int(W), int(H), int(n), rest[0] if rest else None)
    else:
        _, _, raw, outdir, f, n, *rest = sys.argv
        sheet(raw, outdir, float(f), int(n), rest[0] if rest else None)
