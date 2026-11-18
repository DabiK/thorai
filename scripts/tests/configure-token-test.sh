#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")/../.." && pwd)
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
export HOME="$tmp/home" PREFIX="$tmp/prefix"
mkdir -p "$HOME/bin"
for command in start stop; do
  printf '#!/usr/bin/env bash\necho %s >> "$HOME/calls"\n' "$command" > "$HOME/bin/regie-bridge-$command"
  chmod +x "$HOME/bin/regie-bridge-$command"
done
# All external calls are fakes; the token never leaves this fixture.
cat > "$HOME/bin/proot-distro" <<'EOF'
#!/usr/bin/env bash
[[ "$1 $2 $3" == 'login debian --' && "${!#}" == regie-pair-fixture ]] || exit 1
cat "$HOME/token-source"
EOF
printf '#!/usr/bin/env bash\nexit 0\n' > "$HOME/bin/flock"
chmod +x "$HOME/bin/proot-distro" "$HOME/bin/flock"
export PATH="$HOME/bin:$PATH"
token=$(printf 'ab%.0s' {1..32})
printf '%s\n' "$token" > "$HOME/token-source"
bash "$root/thor/bridge/configure.sh" regie-pair-fixture > "$tmp/output" 2>&1
file="$PREFIX/var/lib/proot-distro/containers/debian/rootfs/home/dev/.config/regie/bridge-token"
[[ $(cat "$file") == "$token" ]]
[[ $(stat -f '%Lp' "$file" 2>/dev/null || stat -c '%a' "$file") == 600 ]]
[[ $(cat "$HOME/calls") == $'stop\nstart' ]]
! grep -qF "$token" "$tmp/output"
printf '%s\n' "$token" > "$HOME/token-source"
bash "$root/thor/bridge/configure.sh" regie-pair-fixture > "$tmp/output" 2>&1
[[ $(cat "$HOME/calls") == $'stop\nstart\nstart' ]]
printf 'invalid\n' > "$HOME/token-source"
if bash "$root/thor/bridge/configure.sh" regie-pair-fixture > "$tmp/output" 2>&1; then exit 1; fi
[[ $(cat "$file") == "$token" ]]
echo 'configure token: OK'
