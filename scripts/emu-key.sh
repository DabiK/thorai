#!/usr/bin/env bash
# Usage: emu-key.sh A|B|X|Y|L1|R1|L2|R2|START|UP|DOWN|LEFT|RIGHT [durée-ms] [display]
source "$(dirname "$0")/emu-common.sh"
key="${1:?touche manette requise}"
duration="${2:-0}"
display="${3:-0}"
[[ "$duration" =~ ^[0-9]+$ && "$display" =~ ^[0-9]+$ ]] || exit 2
case "$key" in
    A|B|X|Y|L1|R1|L2|R2|START|SELECT) code="KEYCODE_BUTTON_$key" ;;
    UP|DOWN|LEFT|RIGHT) code="KEYCODE_DPAD_$key" ;;
    *) echo "Touche inconnue : $key" >&2; exit 2 ;;
esac
require_regie_emulator
adb -s "$EMU_SERIAL" shell input gamepad -d "$display" keyevent --duration "$duration" "$code"
