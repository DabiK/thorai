#!/usr/bin/env bash
# Génère, sur le Mac, un WAV par consigne du corpus de mesure de la voix
# (PCM 16 bits, mono, 16 kHz), lu par la voix française « Thomas » de macOS.
#
# Usage : scripts/voice-corpus.sh [dossier_de_sortie] [voix]
#   dossier_de_sortie : par défaut voice/build/corpus (ignoré par git)
#   voix              : par défaut Thomas (voir `say -v '?' | grep fr_`)
#
# Ces enregistrements synthétiques servent aux essais et aux tests. La mesure
# du ticket 04 se fait avec la voix de l'utilisateur, lue sur la Thor.
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
corpus="$root/voice/corpus/consignes.txt"
out="${1:-$root/voice/build/corpus}"
voice="${2:-Thomas}"

command -v say >/dev/null || { echo "say introuvable : ce script tourne sur macOS." >&2; exit 1; }
command -v afconvert >/dev/null || { echo "afconvert introuvable." >&2; exit 1; }

mkdir -p "$out"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

count=0
while IFS='|' read -r id text; do
  [[ -z "${id// }" || "$id" == \#* ]] && continue
  say -v "$voice" -o "$tmp/$id.aiff" -- "$text"
  # LEI16@16000 : entiers 16 bits little-endian à 16 kHz ; -c 1 : mono.
  afconvert -f WAVE -d LEI16@16000 -c 1 "$tmp/$id.aiff" "$out/$id.wav"
  count=$((count + 1))
done < "$corpus"

echo "$count fichiers WAV dans $out"
