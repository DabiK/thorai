#!/bin/bash
# Associe la console au débogage sans fil depuis le Mac (console en USB).
# L'utilisateur lit le port et le code sur l'écran de la console.
#
#   thor/computer-use/pair-from-mac.sh <port d'association> <code à 6 chiffres>
set -euo pipefail

HERE=$(cd "$(dirname "$0")" && pwd)
# shellcheck source-path=SCRIPTDIR source=lib.sh
. "$HERE/lib.sh"

if [ $# -ne 2 ]; then
  echo "usage : $0 <port d'association> <code à 6 chiffres>" >&2
  exit 2
fi
require_console
printf '/opt/thor-android/pair.sh %q %q\n' "$1" "$2" | debian_sh dev
