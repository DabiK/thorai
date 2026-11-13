# shellcheck shell=bash disable=SC2034
# Helpers for the scripts run from the Mac (install.sh, demo.sh, contract-test.sh).
# Sourced, not run. Every command goes through adb, as the Termux user (run-as, debug build).

SERIAL="${THOR_SERIAL:-THOR0000000001}"
TPREFIX=/data/data/com.termux/files/usr
THOME=/data/data/com.termux/files/home
ROOTFS="$TPREFIX/var/lib/proot-distro/containers/debian/rootfs"
# Debian side (user dev).
DEB_APP=/home/dev/.local/share/regie/bridge
DEB_TOKEN=/home/dev/.config/regie/bridge-token
BRIDGE_PORT="${REGIE_BRIDGE_PORT:-47600}"
# The app (ticket 01): its token file, in its private storage.
APP_ID=app.regie
APP_TOKEN=files/bridge-token

# termux_sh '<bash script>': runs the script in Termux. Stdin is forwarded.
termux_sh() {
  local b64
  b64=$(printf '%s' "$1" | base64 | tr -d '\n')
  adb -s "$SERIAL" shell "run-as com.termux $TPREFIX/bin/env HOME=$THOME PREFIX=$TPREFIX \
TMPDIR=$TPREFIX/tmp PATH=$TPREFIX/bin:/system/bin LD_PRELOAD=$TPREFIX/lib/libtermux-exec.so \
LANG=en_US.UTF-8 $TPREFIX/bin/bash -lc 'eval \"\$(echo $b64 | base64 -d)\"'"
}

# deb_sh '<bash script>': runs the script in the Debian, as dev, with the binds of docs/adr/02.
deb_sh() {
  local b64
  b64=$(printf '%s' "$1" | base64 | tr -d '\n')
  termux_sh "proot-distro login debian --user dev \
--bind \"\$HOME/projects:/home/dev/projects\" --bind \"\$HOME/.codex:/home/dev/.codex\" \
--work-dir /home/dev -- bash -lc \"\$(echo $b64 | base64 -d)\""
}

# deploy_sources <bridge dir on the Mac> <Debian dir>: copies the sources, without node_modules.
deploy_sources() {
  local src="$1" dest="$2"
  COPYFILE_DISABLE=1 tar -C "$src" -czf - package.json package-lock.json tsconfig.json \
    vitest.config.ts vitest.contract.config.ts src scripts |
    deb_sh "set -e; mkdir -p '$dest'; rm -rf '$dest/src' '$dest/scripts'; tar -C '$dest' -xzf -"
}

need_device() {
  if ! adb -s "$SERIAL" get-state >/dev/null 2>&1; then
    echo "Console $SERIAL introuvable par adb." >&2
    exit 1
  fi
}
