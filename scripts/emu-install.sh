#!/usr/bin/env bash
# Third command: install only this repository's app (regie_app_id), inject bench token, run the
# real Codex demo.
# --no-demo updates the APK without starting an agent.
source "$(dirname "$0")/emu-common.sh"
source "$(dirname "$0")/device.sh"
APP_ID="$(regie_app_id)"
require_regie_emulator
export REGIE_BENCH_DIR="$REGIE_BENCH"
[[ -f "$REGIE_BENCH/bridge-token" ]] || { echo 'Lancer scripts/bridge-mac.sh avant.' >&2; exit 1; }
node "$REGIE_ROOT/scripts/emu-client.mjs" ping
if [[ ! -f "$REGIE_ROOT/android/local.properties" ]]; then
    printf 'sdk.dir=%s\n' "${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}" > "$REGIE_ROOT/android/local.properties"
fi
(cd "$REGIE_ROOT/android" && ./gradlew --quiet :app:assembleDebug)
adb -s "$EMU_SERIAL" install -r "$REGIE_ROOT/android/app/build/outputs/apk/debug/app-debug.apk"
adb -s "$EMU_SERIAL" shell am force-stop "$APP_ID"
adb -s "$EMU_SERIAL" shell run-as "$APP_ID" mkdir -p files
adb -s "$EMU_SERIAL" shell "run-as $APP_ID sh -c 'umask 077; cat > files/bridge-token'" < "$REGIE_BENCH/bridge-token"
reverse_bridge
# Bench emulator only: avoid the first-run notification dialog hiding the two activities.
adb -s "$EMU_SERIAL" shell pm grant "$APP_ID" android.permission.POST_NOTIFICATIONS
adb -s "$EMU_SERIAL" shell am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p "$APP_ID"
if [[ "${1:-}" != --no-demo ]]; then
    node "$REGIE_ROOT/scripts/emu-client.mjs" demo
    session="$(cat "$REGIE_BENCH/demo-session")"
    adb -s "$EMU_SERIAL" shell am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p "$APP_ID" --es session_id "$session"
fi
