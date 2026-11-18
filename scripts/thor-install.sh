#!/usr/bin/env bash
# Construit l'APK debug de Régie, l'installe sur la Thor et le lance.
# N'installe que app.regie : rien n'est désinstallé sur la console partagée.
#
# Usage : scripts/thor-install.sh [arguments supplémentaires pour « am start »]
#   ex. : scripts/thor-install.sh --ez force_single_screen true
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/device.sh"
SERIAL="$(select_adb_device)"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ANDROID="$ROOT/android"
APK="$ANDROID/app/build/outputs/apk/debug/app-debug.apk"

if [ ! -f "$ANDROID/local.properties" ]; then
	echo "sdk.dir=${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}" >"$ANDROID/local.properties"
fi

(cd "$ANDROID" && ./gradlew --quiet :app:assembleDebug)

adb -s "$SERIAL" install -r "$APK"
adb -s "$SERIAL" shell am start -n app.regie/.TopActivity "$@"
