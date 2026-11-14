#!/usr/bin/env bash
# Usage: emu-shot.sh <folder> [prefix]; both screenshots are required.
source "$(dirname "$0")/emu-common.sh"
require_regie_emulator
dir="${1:?usage: scripts/emu-shot.sh <dossier> [préfixe]}"
prefix="${2:-}"
mkdir -p "$dir"
ids="$(adb -s "$EMU_SERIAL" shell dumpsys SurfaceFlinger --display-id)"
top="$(printf '%s\n' "$ids" | sed -nE 's/^Display ([0-9]+) \(HWC display 0\).*/\1/p')"
bottom="$(printf '%s\n' "$ids" | sed -nE 's/^Display ([0-9]+).*displayName="Emulator 2D Display".*/\1/p')"
[[ "$top" =~ ^[0-9]+$ && "$bottom" =~ ^[0-9]+$ ]] || {
    echo 'Deux écrans non ambigus requis ; relancer emu-start.sh.' >&2; exit 1;
}
tmp="$(mktemp -d "$dir/.capture.XXXXXX")"
trap 'rm -rf "$tmp"' EXIT
adb -s "$EMU_SERIAL" exec-out screencap -p -d "$top" > "$tmp/haut.png"
adb -s "$EMU_SERIAL" exec-out screencap -p -d "$bottom" > "$tmp/bas.png"
python3 - "$tmp" <<'PNG'
import pathlib, struct, sys
for name, size in [('haut', (1920, 1080)), ('bas', (1240, 1080))]:
    data = (pathlib.Path(sys.argv[1]) / f'{name}.png').read_bytes()
    if data[:8] != b'\x89PNG\r\n\x1a\n' or struct.unpack('>II', data[16:24]) != size:
        raise SystemExit(f'Capture {name} invalide ou résolution incorrecte')
PNG
mv "$tmp/haut.png" "$dir/${prefix}haut.png"
mv "$tmp/bas.png" "$dir/${prefix}bas.png"
printf '%s\n' "$dir/${prefix}haut.png" "$dir/${prefix}bas.png"
