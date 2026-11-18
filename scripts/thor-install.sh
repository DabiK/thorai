#!/usr/bin/env bash
# Construit l'APK debug (ou --release avec la signature existante) de Régie, l'installe sur la Thor et le lance.
# N'installe que le paquet de ce dépôt (regie_app_id : app.regie.jeu sur la branche du jeu, à
# côté de l'app classique) : rien n'est désinstallé sur la console partagée.
#
# Usage : scripts/thor-install.sh [--release] [arguments supplémentaires pour « am start »]
#   ex. : scripts/thor-install.sh --ez force_single_screen true
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/device.sh"
SERIAL="$(select_adb_device)"
APP_ID="$(regie_app_id)"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ANDROID="$ROOT/android"
variant=Debug
if [[ "${1:-}" == --release ]]; then
    shift
    variant=Release
    export REGIE_RELEASE_CONFIG="${REGIE_RELEASE_CONFIG:-$HOME/.config/regie/release.properties}"
    [[ -f "$REGIE_RELEASE_CONFIG" ]] || { echo 'Configuration de signature release absente.' >&2; exit 1; }
fi
APK="$ANDROID/app/build/outputs/apk/$(echo "$variant" | tr '[:upper:]' '[:lower:]')/app-$(echo "$variant" | tr '[:upper:]' '[:lower:]').apk"

if [ ! -f "$ANDROID/local.properties" ]; then
	echo "sdk.dir=${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}" >"$ANDROID/local.properties"
fi

(cd "$ANDROID" && ./gradlew --quiet :app:assemble"$variant")

adb -s "$SERIAL" install -r "$APK"
# Par le lanceur du paquet : le nom de la classe ne suit pas le suffixe du paquet. « am start -p »
# exige CATEGORY_DEFAULT, qu'un lanceur n'a pas : on résout d'abord son activité.
launcher="$(adb -s "$SERIAL" shell cmd package resolve-activity --brief -c android.intent.category.LAUNCHER "$APP_ID" | tail -n 1 | tr -d '\r')"
adb -s "$SERIAL" shell am start -n "$launcher" "$@"
