#!/usr/bin/env bash
# No device: adb is a shell fake.
set -euo pipefail
# Each [[ ]] ends with || exit 1: bash 3.2 (macOS) does not stop on a failed [[ ]] under set -e.
root=$(cd "$(dirname "$0")/../.." && pwd)
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
cat > "$tmp/adb" <<'EOF'
#!/usr/bin/env bash
[[ "${FAKE_ADB_FAILURE:-0}" == 0 ]] || exit 1
printf '%s\n' 'List of devices attached' "$FAKE_DEVICES"
EOF
chmod +x "$tmp/adb"
export PATH="$tmp:$PATH"
unset THOR_SERIAL
export FAKE_DEVICES=$'unique\tdevice'
[[ $(source "$root/scripts/device.sh"; select_adb_device) == unique ]] || exit 1
export FAKE_DEVICES=$'one\tdevice\ntwo\tdevice'
if (source "$root/scripts/device.sh"; select_adb_device) >"$tmp/out" 2>"$tmp/error"; then exit 1; fi
grep -q THOR_SERIAL "$tmp/error"
[[ $(export THOR_SERIAL=two; source "$root/scripts/device.sh"; select_adb_device) == two ]] || exit 1
export FAKE_DEVICES=$'one\tdevice\ntwo\tunauthorized'
if (source "$root/scripts/device.sh"; select_adb_device) >"$tmp/out" 2>"$tmp/error"; then exit 1; fi
export FAKE_DEVICES=$'one\tunauthorized'
if (source "$root/scripts/device.sh"; select_adb_device) >"$tmp/out" 2>"$tmp/error"; then exit 1; fi
if (export THOR_SERIAL=missing; source "$root/scripts/device.sh"; select_adb_device) >"$tmp/out" 2>"$tmp/error"; then exit 1; fi
export FAKE_DEVICES=$'one\tdevice'
export FAKE_ADB_FAILURE=1
if (source "$root/scripts/device.sh"; select_adb_device) >"$tmp/out" 2>"$tmp/error"; then exit 1; fi

# The installed package follows android/app/build.gradle.kts: id plus suffix (ticket 50).
unset FAKE_ADB_FAILURE REGIE_APP_ID
printf '%s\n' 'android {' '    defaultConfig {' '        applicationId = "app.thorai"' '    }' \
  '    buildTypes {' '        configureEach { applicationIdSuffix = ".jeu" }' '    }' '}' > "$tmp/suffixed.gradle.kts"
[[ $(source "$root/scripts/device.sh"; regie_app_id "$tmp/suffixed.gradle.kts") == app.thorai.jeu ]] || exit 1
printf '%s\n' '        applicationId = "app.regie"' > "$tmp/plain.gradle.kts"
[[ $(source "$root/scripts/device.sh"; regie_app_id "$tmp/plain.gradle.kts") == app.regie ]] || exit 1
[[ $(export REGIE_APP_ID=app.regie; source "$root/scripts/device.sh"; regie_app_id "$tmp/suffixed.gradle.kts") == app.regie ]] || exit 1
printf '%s\n' 'android {}' > "$tmp/none.gradle.kts"
if (source "$root/scripts/device.sh"; regie_app_id "$tmp/none.gradle.kts") >"$tmp/out" 2>"$tmp/error"; then exit 1; fi
# Without argument: this repository's app.
[[ $(source "$root/scripts/device.sh"; regie_app_id) =~ ^[a-z]+(\.[a-z]+)+$ ]] || exit 1
echo 'device selection: OK'
