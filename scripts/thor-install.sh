#!/usr/bin/env bash
# Construit l'APK debug de Régie, l'installe sur la Thor et le lance.
# N'installe que le paquet de ce dépôt (regie_app_id : app.regie.jeu sur la branche du jeu, à
# côté de l'app classique) : rien n'est désinstallé sur la console partagée.
#
# Usage : scripts/thor-install.sh [arguments supplémentaires pour « am start »]
#   ex. : scripts/thor-install.sh --ez force_single_screen true
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/device.sh"
SERIAL="$(select_adb_device)"
APP_ID="$(regie_app_id)"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ANDROID="$ROOT/android"
APK="$ANDROID/app/build/outputs/apk/debug/app-debug.apk"

if [ ! -f "$ANDROID/local.properties" ]; then
	echo "sdk.dir=${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}" >"$ANDROID/local.properties"
fi

(cd "$ANDROID" && ./gradlew --quiet :app:assembleDebug)

adb -s "$SERIAL" install -r "$APK"
# Par le lanceur du paquet : le nom de la classe ne suit pas le suffixe du paquet.
adb -s "$SERIAL" shell am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p "$APP_ID" "$@"
