#!/usr/bin/env bash
# No device: adb is a shell fake.
set -euo pipefail
root=$(cd "$(dirname "$0")/../.." && pwd)
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
cat > "$tmp/adb" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' 'List of devices attached' "$FAKE_DEVICES"
EOF
chmod +x "$tmp/adb"
export PATH="$tmp:$PATH"
unset THOR_SERIAL
export FAKE_DEVICES=$'unique\tdevice'
[[ $(source "$root/scripts/device.sh"; select_adb_device) == unique ]]
export FAKE_DEVICES=$'one\tdevice\ntwo\tdevice'
if (source "$root/scripts/device.sh"; select_adb_device) >"$tmp/out" 2>"$tmp/error"; then exit 1; fi
grep -q THOR_SERIAL "$tmp/error"
[[ $(export THOR_SERIAL=two; source "$root/scripts/device.sh"; select_adb_device) == two ]]
export FAKE_DEVICES=$'one\tunauthorized'
if (source "$root/scripts/device.sh"; select_adb_device) >"$tmp/out" 2>"$tmp/error"; then exit 1; fi
if (export THOR_SERIAL=missing; source "$root/scripts/device.sh"; select_adb_device) >"$tmp/out" 2>"$tmp/error"; then exit 1; fi
echo 'device selection: OK'
