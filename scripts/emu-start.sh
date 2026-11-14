#!/usr/bin/env bash
# Creates only Regie_Thor; never changes or stops another AVD.
source "$(dirname "$0")/emu-common.sh"
sdk="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"
export ANDROID_HOME="$sdk"
avd_dir="${ANDROID_AVD_HOME:-$HOME/.android/avd}"
mkdir -p "$REGIE_BENCH"
if adb -s "$EMU_SERIAL" get-state >/dev/null 2>&1; then
    require_regie_emulator
else
    if [[ ! -f "$avd_dir/Regie_Thor.avd/config.ini" ]]; then
        image='system-images;android-36;google_apis_playstore;arm64-v8a'
        [[ -d "$sdk/system-images/android-36/google_apis_playstore/arm64-v8a" ]] || {
            echo "Image absente : sdkmanager '$image'" >&2; exit 1;
        }
        echo no | "$sdk/cmdline-tools/latest/bin/avdmanager" create avd --name Regie_Thor --package "$image"
        cat >> "$avd_dir/Regie_Thor.avd/config.ini" <<'CONFIG'
hw.lcd.width=1920
hw.lcd.height=1080
hw.lcd.density=369
hw.initialOrientation=landscape
hw.ramSize=4096
hw.keyboard=yes
hw.gpu.enabled=yes
hw.gpu.mode=auto
showDeviceFrame=no
skin.name=1920x1080
skin.path=_no_skin
CONFIG
    fi
    # Detached process survives the shell; no boot service or snapshot from another AVD.
    python3 - "$sdk/emulator/emulator" "$EMU_PORT" "$REGIE_BENCH/emulator.log" <<'SPAWN'
import subprocess, sys
with open(sys.argv[3], 'a') as log:
    subprocess.Popen([sys.argv[1], '-avd', 'Regie_Thor', '-port', sys.argv[2],
                      '-no-snapshot', '-no-boot-anim'], stdin=subprocess.DEVNULL,
                     stdout=log, stderr=log, start_new_session=True)
SPAWN
fi
end=$((SECONDS + ${EMU_BOOT_TIMEOUT:-240}))
until [[ "$(adb -s "$EMU_SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == 1 ]]; do
    if (( SECONDS >= end )); then echo "Démarrage expiré : $REGIE_BENCH/emulator.log" >&2; exit 1; fi
    sleep 2
done
require_regie_emulator
# sys.boot_completed precedes the emulator multidisplay service on a cold boot.
end=$((SECONDS + 60))
while true; do
    result="$(adb -s "$EMU_SERIAL" emu multidisplay add 1 1240 1080 369 0 2>&1)" || true
    [[ "$result" == *OK* && "$result" != *KO:* ]] && break
    if (( SECONDS >= end )); then echo "$result" >&2; exit 1; fi
    sleep 2
done
printf '%s\n' "$result"
[[ "$result" != *KO:* ]] || exit 1
adb -s "$EMU_SERIAL" shell settings put system accelerometer_rotation 0
adb -s "$EMU_SERIAL" shell settings put system user_rotation 0
adb -s "$EMU_SERIAL" shell input keyevent KEYCODE_WAKEUP
adb -s "$EMU_SERIAL" shell wm dismiss-keyguard
echo "Regie_Thor prêt ($EMU_SERIAL), écrans 1920×1080 et 1240×1080."
