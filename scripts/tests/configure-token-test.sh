#!/usr/bin/env bash
set -euo pipefail
# Each [[ ]] ends with || exit 1: bash 3.2 (macOS) does not stop on a failed [[ ]] under set -e.
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
[[ $(cat "$file") == "$token" ]] || exit 1
mode=$(stat -c '%a' "$file" 2>/dev/null) || mode=$(stat -f '%Lp' "$file")
[[ "$mode" == 600 ]] || exit 1
[[ $(cat "$HOME/calls") == $'stop\nstart' ]] || exit 1
if grep -qF "$token" "$tmp/output"; then exit 1; fi
printf '%s\n' "$token" > "$HOME/token-source"
bash "$root/thor/bridge/configure.sh" regie-pair-fixture > "$tmp/output" 2>&1
[[ $(cat "$HOME/calls") == $'stop\nstart\nstart' ]] || exit 1
printf 'invalid\n' > "$HOME/token-source"
if bash "$root/thor/bridge/configure.sh" regie-pair-fixture > "$tmp/output" 2>&1; then exit 1; fi
[[ $(cat "$file") == "$token" ]] || exit 1

# A second app (ticket 50, ADR 22) names itself: its own file, the classic token kept, and the
# bridge, which reads the tokens at each connection, is not stopped.
game=$(printf 'cd%.0s' {1..32})
printf '%s\n' "$game" > "$HOME/token-source"
: > "$HOME/calls"
bash "$root/thor/bridge/configure.sh" regie-pair-fixture app.regie.jeu > "$tmp/output" 2>&1
client_file="$(dirname "$file")/bridge-tokens/app.regie.jeu"
[[ $(cat "$client_file") == "$game" ]] || exit 1
mode=$(stat -c '%a' "$client_file" 2>/dev/null) || mode=$(stat -f '%Lp' "$client_file")
[[ "$mode" == 600 ]] || exit 1
mode=$(stat -c '%a' "$(dirname "$client_file")" 2>/dev/null) || mode=$(stat -f '%Lp' "$(dirname "$client_file")")
[[ "$mode" == 700 ]] || exit 1
[[ $(cat "$file") == "$token" ]] || exit 1
[[ $(cat "$HOME/calls") == start ]] || exit 1
if grep -qF "$game" "$tmp/output"; then exit 1; fi
[[ -z $(find "$(dirname "$client_file")" -name '.*' -type f) ]] || exit 1
# A new token for that app replaces only its file, still without stopping the bridge.
game=$(printf 'ef%.0s' {1..32})
printf '%s\n' "$game" > "$HOME/token-source"
bash "$root/thor/bridge/configure.sh" regie-pair-fixture app.regie.jeu > "$tmp/output" 2>&1
[[ $(cat "$client_file") == "$game" ]] || exit 1
[[ $(cat "$file") == "$token" ]] || exit 1
[[ $(cat "$HOME/calls") == $'start\nstart' ]] || exit 1
# The classic app, as it is today (one argument), keeps its file and the other app's.
printf '%s\n' "$token" > "$HOME/token-source"
bash "$root/thor/bridge/configure.sh" regie-pair-fixture > "$tmp/output" 2>&1
[[ $(cat "$client_file") == "$game" ]] || exit 1
[[ $(cat "$HOME/calls") == $'start\nstart\nstart' ]] || exit 1
# Only a package name: no path, no hidden file, no third argument.
for client in ../bridge-token bridge-token .app.regie app 'app.regie/x' 'app.regie jeu' ''; do
  if bash "$root/thor/bridge/configure.sh" regie-pair-fixture "$client" > "$tmp/output" 2>&1; then
    echo "client accepted: $client" >&2; exit 1
  fi
done
if bash "$root/thor/bridge/configure.sh" regie-pair-fixture app.regie.jeu extra > "$tmp/output" 2>&1; then exit 1; fi
[[ $(cat "$file") == "$token" && $(cat "$client_file") == "$game" ]] || exit 1
[[ $(cat "$HOME/calls") == $'start\nstart\nstart' ]] || exit 1
echo 'configure token: OK'
