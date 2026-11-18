#!/data/data/com.termux/files/usr/bin/bash
# Run from a checkout in Termux. Idempotent; never installs an Android package.
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
[[ -n "${PREFIX:-}" && -d "$PREFIX" ]] || { echo 'Lancer ce script dans Termux.' >&2; exit 1; }
export REGIE_ON_DEVICE=1
missing=()
command -v proot-distro >/dev/null 2>&1 || missing+=(proot-distro)
command -v curl >/dev/null 2>&1 || missing+=(curl)
if ! command -v flock >/dev/null 2>&1 || ! command -v setsid >/dev/null 2>&1; then missing+=(util-linux); fi
command -v git >/dev/null 2>&1 || missing+=(git)
if (( ${#missing[@]} )); then
  pkg update -y
  pkg install -y "${missing[@]}"
fi
mkdir -p "$HOME/projects" "$HOME/.codex"
rootfs="$PREFIX/var/lib/proot-distro/containers/debian/rootfs"
if [[ ! -f "$rootfs/etc/debian_version" ]]; then
  proot-distro install --name debian debian:13
fi
proot-distro login debian -- /bin/bash -s <<'DEBIAN'
{
set -euo pipefail
apt-get update -qq
DEBIAN_FRONTEND=noninteractive apt-get install -y -qq ca-certificates curl xz-utils git jq procps python3 ripgrep
id dev >/dev/null 2>&1 || useradd --create-home --shell /bin/bash dev
mkdir -p /home/dev/projects /home/dev/.codex
chown dev:dev /home/dev /home/dev/projects /home/dev/.codex
if [[ $(node --version 2>/dev/null || true) != v24.22.3 ]]; then
  work=$(mktemp -d)
  trap 'rm -rf "$work"' EXIT
  cd "$work"
  curl -fLO https://nodejs.org/dist/v24.22.3/node-v24.22.3-linux-arm64.tar.xz
  curl -fLO https://nodejs.org/dist/v24.22.3/SHASUMS256.txt
  grep ' node-v24.22.3-linux-arm64.tar.xz$' SHASUMS256.txt | sha256sum -c -
  tar -xJf node-v24.22.3-linux-arm64.tar.xz -C /opt
  for binary in node npm npx; do ln -sf "/opt/node-v24.22.3-linux-arm64/bin/$binary" "/usr/local/bin/$binary"; done
fi
if [[ $(/usr/local/bin/codex --version 2>/dev/null || true) != 'codex-cli 0.167.2' ]]; then
  npm install --global --prefix /opt/codex-cli @openai/codex@0.167.2
  ln -sf /opt/codex-cli/bin/codex /usr/local/bin/codex
fi
}
DEBIAN
proot-distro login debian --user dev \
  --bind "$HOME/projects:/home/dev/projects" --bind "$HOME/.codex:/home/dev/.codex" \
  --work-dir /home/dev -- /bin/bash -s <<'DEV'
{
set -euo pipefail
export PATH="$HOME/.local/bin:$HOME/.opencode/bin:$PATH"
if [[ $(claude --version 2>/dev/null || true) != '2.1.322 (Claude Code)' ]]; then
  installer=$(mktemp)
  trap 'rm -f "$installer"' EXIT
  curl -fsSL https://claude.ai/install.sh -o "$installer"
  bash "$installer" 2.1.322
fi
profile_line='export PATH="$HOME/.local/bin:$HOME/.opencode/bin:/usr/local/bin:$PATH"'
touch "$HOME/.profile"
grep -Fxq "$profile_line" "$HOME/.profile" || printf '\n%s\n' "$profile_line" >> "$HOME/.profile"
}
DEV
proot-distro login debian --user dev \
  --bind "$HOME/.codex:/home/dev/.codex" -- python3 - /home/dev/.codex/config.toml < "$here/configure-codex.py"
bash "$here/bridge/install.sh"
bash "$here/computer-use/install.sh"
cat <<'EOF'
Installation terminée. Rien ne démarre au boot.
1. Installer l'APK Régie signé depuis la release GitHub, puis ouvrir Régie.
2. Autoriser les commandes Termux et les notifications demandées par Android.
3. Dans Paramètres Android > Options pour les développeurs > Débogage sans fil,
   activer le débogage puis « Associer l'appareil avec un code d'association ».
4. Dans Termux, entrer dans la Debian :
   proot-distro login debian --user dev --bind "$HOME/projects:/home/dev/projects" --bind "$HOME/.codex:/home/dev/.codex" --work-dir /home/dev -- bash -l
5. Exécuter /opt/thor-android/pair.sh <port> <code>, puis :
   adb shell pm grant app.regie android.permission.WRITE_SECURE_SETTINGS
   adb shell settings put global settings_enable_monitor_phantom_procs false
6. Revenir dans Régie et connecter ses comptes (B). Retirer Termux de
   l'optimisation de batterie et activer le mode console dans Accessibilité.
EOF
