#!/usr/bin/env bash
# Capture les deux écrans de la Thor.
#
# Usage : scripts/thor-shot.sh <dossier> [préfixe]
#   écrit <dossier>/<préfixe>haut.png et <dossier>/<préfixe>bas.png
#
# « screencap -d » attend l'identifiant physique de l'écran. On relie le
# displayId logique (0 en haut, 4 en bas) à son identifiant physique par le
# uniqueId « local:<id> » de « dumpsys display », et on vérifie qu'il figure
# dans « dumpsys SurfaceFlinger --display-id ». À défaut, on prend l'ordre de
# SurfaceFlinger (premier écran en haut, second en bas).
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/device.sh"
SERIAL="$(select_adb_device)"
TOP_DISPLAY="${TOP_DISPLAY:-0}"
BOTTOM_DISPLAY="${BOTTOM_DISPLAY:-4}"

dir="${1:?usage: scripts/thor-shot.sh <dossier> [préfixe]}"
prefix="${2:-}"
mkdir -p "$dir"

physical_ids="$(adb -s "$SERIAL" shell dumpsys SurfaceFlinger --display-id |
	sed -nE 's/^Display ([0-9]+) .*/\1/p')"
[ -n "$physical_ids" ] || {
	echo "aucun écran trouvé par SurfaceFlinger" >&2
	exit 1
}

mapping="$(adb -s "$SERIAL" shell dumpsys display |
	sed -nE 's/.*mBaseDisplayInfo=DisplayInfo\{.*displayId ([0-9]+)".*uniqueId "local:([0-9]+)".*/\1 \2/p' |
	sort -u)"

physical_for() { # <displayId> <rang de repli dans SurfaceFlinger>
	local id
	id="$(echo "$mapping" | awk -v d="$1" '$1 == d { print $2; exit }')"
	if [ -n "$id" ] && echo "$physical_ids" | grep -qx "$id"; then
		echo "$id"
	else
		echo "$physical_ids" | sed -n "${2}p"
	fi
}

top="$(physical_for "$TOP_DISPLAY" 1)"
bottom="$(physical_for "$BOTTOM_DISPLAY" 2)"

adb -s "$SERIAL" exec-out screencap -p -d "$top" >"$dir/${prefix}haut.png"
echo "$dir/${prefix}haut.png (display $TOP_DISPLAY, écran physique $top)"
if [ -n "$bottom" ]; then
	adb -s "$SERIAL" exec-out screencap -p -d "$bottom" >"$dir/${prefix}bas.png"
	echo "$dir/${prefix}bas.png (display $BOTTOM_DISPLAY, écran physique $bottom)"
else
	echo "écran du bas introuvable : seule la capture du haut est faite" >&2
fi
