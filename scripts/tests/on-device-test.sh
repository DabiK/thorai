#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")/../.." && pwd)
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/thor/bridge" "$tmp/thor/computer-use" "$tmp/prefix" "$tmp/bin"
cp "$root/thor/install-on-device.sh" "$tmp/thor/"
cp "$root/thor/configure-codex.py" "$tmp/thor/"
for part in bridge computer-use; do printf 'echo %s >> "$ON_DEVICE_LOG"\n' "$part" > "$tmp/thor/$part/install.sh"; done
cat > "$tmp/bin/pkg" <<'EOF'
#!/usr/bin/env bash
echo 'unexpected pkg call: dependencies already installed' >&2
exit 79
EOF
cat > "$tmp/bin/proot-distro" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$ON_DEVICE_LOG"
if [[ "$1" == install ]]; then
  mkdir -p "$PREFIX/var/lib/proot-distro/containers/debian/rootfs/etc"
  echo 13 > "$PREFIX/var/lib/proot-distro/containers/debian/rootfs/etc/debian_version"
else
  cat >/dev/null
fi
EOF
printf '#!/usr/bin/env bash\nexit 0\n' > "$tmp/bin/flock"
printf '#!/usr/bin/env bash\nexit 0\n' > "$tmp/bin/setsid"
chmod +x "$tmp/bin/"*
for pass in 1 2; do
  env HOME="$tmp/home" PREFIX="$tmp/prefix" ON_DEVICE_LOG="$tmp/log" PATH="$tmp/bin:$PATH" \
    bash "$tmp/thor/install-on-device.sh" > "$tmp/output"
done
[[ $(grep -c '^install --name debian debian:13$' "$tmp/log") == 1 ]]
[[ $(grep -c '^bridge$' "$tmp/log") == 2 ]]
[[ $(grep -c '^computer-use$' "$tmp/log") == 2 ]]
grep -q '/opt/thor-android/pair.sh <port> <code>' "$tmp/output"
echo 'on-device installation orchestration/idempotence: OK'
