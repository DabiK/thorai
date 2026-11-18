"""Check assets/game/ against the contract read by GameAssetLoader (ticket 41) and the village engine.

usage: verifier_assets.py <assets/game dir>
Mirrors AssetJson.parseAtlas / parsePalette (required fields, known frames, three roof keys),
then adds: frames inside their PNG, anchors inside their frame, house footprint <= 7 x 5 with the
door on the last row, ids unique across atlases (an id resolves to the first atlas that has it).
"""
import json
import os
import re
import sys
from PIL import Image

ATLASES = ["personnages", "maisons", "decor", "icones", "fonds"]
COLOR = re.compile(r"^#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$")
ROLES = [g + "." + t for g in ("grass", "path", "plaza", "water", "bridge") for t in ("light", "base", "dark")] + ["outline"]


def fail(msg):
    raise SystemExit("ERREUR " + msg)


def is_int(v):
    return isinstance(v, int) and not isinstance(v, bool)


def check_palette(path):
    p = json.load(open(os.path.join(path, "palette.json")))
    for c in p.get("colors", []):
        if not COLOR.match(c):
            fail("palette : couleur %s" % c)
    roles = p.get("roles", {})
    for k, v in roles.items():
        if not isinstance(v, str) or not COLOR.match(v):
            fail("palette : rôle %s" % k)
        if v.upper() not in {c.upper() for c in p["colors"]}:
            fail("palette : rôle %s hors des 32 couleurs" % k)
    missing = [r for r in ROLES if r not in roles]
    if missing:
        fail("palette : rôles absents %s" % missing)
    print("palette.json : %d couleurs, %d rôles" % (len(p["colors"]), len(roles)))


def check_atlas(path, name, seen):
    spec = json.load(open(os.path.join(path, name + ".json")))
    img = Image.open(os.path.join(path, name + ".png"))
    W, H = img.size
    if not is_int(spec.get("pixel", 1)) or spec.get("pixel", 1) < 1:
        fail("%s : pixel" % name)
    frames = spec.get("frames", {})
    for n, f in frames.items():
        for k in ("x", "y", "w", "h", "ax", "ay"):
            if not is_int(f.get(k)):
                fail("%s : image %s, champ %s" % (name, n, k))
        if f["w"] <= 0 or f["h"] <= 0 or f["x"] < 0 or f["y"] < 0 or f["x"] + f["w"] > W or f["y"] + f["h"] > H:
            fail("%s : image %s hors de l'atlas" % (name, n))
        if not (0 <= f["ax"] <= f["w"] and 0 <= f["ay"] < f["h"]):
            fail("%s : ancrage de %s hors de l'image" % (name, n))
    anims = spec.get("anims", {})
    for n, a in anims.items():
        if not a.get("frames") or not is_int(a.get("ms")) or a["ms"] <= 0:
            fail("%s : animation %s" % (name, n))
        for f in a["frames"]:
            if f not in frames:
                fail("%s : animation %s, image %s inconnue" % (name, n, f))
    objects = spec.get("objects", {})
    for n, o in objects.items():
        fr = o.get("frame", n)
        if fr not in frames and fr not in anims:
            fail("%s : objet %s, image %s inconnue" % (name, n, fr))
        fp = o.get("footprint", {"w": 1, "h": 1})
        if not (is_int(fp.get("w")) and is_int(fp.get("h")) and fp["w"] > 0 and fp["h"] > 0):
            fail("%s : objet %s, empreinte" % (name, n))
        keys = o.get("roofKeys", [])
        if keys and (len(keys) != 3 or not all(COLOR.match(k) for k in keys)):
            fail("%s : objet %s, couleurs clés" % (name, n))
        door = o.get("door")
        if door is not None:
            if not (is_int(door.get("tx")) and is_int(door.get("ty"))):
                fail("%s : objet %s, porte" % (name, n))
            if not (0 <= door["tx"] < fp["w"] and door["ty"] == fp["h"] - 1):
                fail("%s : objet %s, la porte doit être sur la dernière rangée" % (name, n))
        if n.startswith("maison.") and (fp["w"] > 7 or fp["h"] > 5):
            fail("%s : objet %s, empreinte au-delà de 7 x 5" % (name, n))
        if n.startswith("maison."):
            f = frames[fr]
            c = o.get("chimney")
            if c and not (0 <= c["x"] < f["w"] and 0 <= c["y"] < f["h"]):
                fail("%s : cheminée de %s hors de l'image" % (name, n))
            for r in o.get("windows", []):
                if r["x"] < 0 or r["y"] < 0 or r["x"] + r["w"] > f["w"] or r["y"] + r["h"] > f["h"]:
                    fail("%s : fenêtre de %s hors de l'image" % (name, n))
    for ids in (frames, anims, objects):
        for i in ids:
            if i in seen and seen[i] != name:
                fail("identifiant %s dans %s et dans %s" % (i, seen[i], name))
            seen[i] = name
    print("%s : %dx%d, %d images, %d animations, %d objets" % (name, W, H, len(frames), len(anims), len(objects)))


def main():
    path = sys.argv[1]
    check_palette(path)
    seen = {}
    for a in ATLASES:
        check_atlas(path, a, seen)
    print("OK")


if __name__ == "__main__":
    main()
