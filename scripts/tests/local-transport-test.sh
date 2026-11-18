#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")/../.." && pwd)
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/bin"
cat > "$tmp/bin/adb" <<'EOF'
#!/usr/bin/env bash
exit 91
EOF
cat > "$tmp/bin/proot-distro" <<'EOF'
#!/usr/bin/env bash
[[ "$*" == 'login debian --user dev --bind '* ]]
cat
EOF
chmod +x "$tmp/bin/"*
export PATH="$tmp/bin:$PATH" REGIE_ON_DEVICE=1
source "$root/thor/computer-use/lib.sh"
require_console
[[ $(printf 'local-ok\n' | debian_sh dev) == local-ok ]]
[[ $(printf 'echo termux-ok\n' | termux_sh) == termux-ok ]]
source "$root/thor/bridge/lib.sh"
need_device
[[ $(termux_sh 'printf bridge-ok') == bridge-ok ]]
echo 'local transports (no adb): OK'
