#!/data/data/com.termux/files/usr/bin/bash
# RUN_COMMAND carries only an abstract Unix socket name, never the token itself.
#   regie-bridge-configure <socket>            the historical token (an app that names no client)
#   regie-bridge-configure <socket> <package>  the token of this app, next to the others (ADR 22)
set +x
set -euo pipefail
[[ ($# == 1 || $# == 2) && "$1" =~ ^regie-pair-[a-zA-Z0-9-]+$ ]] || { echo 'Socket de configuration invalide.' >&2; exit 1; }
package_name='^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$'
client=''
if [[ $# == 2 ]]; then
  [[ "$2" =~ $package_name ]] || { echo 'Application de configuration invalide.' >&2; exit 1; }
  client=$2
fi
# Debian's Python uses the same outer Android UID as Termux. No token in argv/stdin logs.
token=$(proot-distro login debian -- python3 -c '
import socket, sys
with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as peer:
    peer.settimeout(15)
    peer.connect("\0" + sys.argv[1])
    chunks = []
    while True:
        chunk = peer.recv(128)
        if not chunk:
            break
        chunks.append(chunk)
    sys.stdout.write(b"".join(chunks).decode("ascii"))
' "$1")
[[ "$token" =~ ^[0-9a-f]{64}$ ]] || { echo 'Jeton de configuration invalide.' >&2; exit 1; }
rootfs="$PREFIX/var/lib/proot-distro/containers/debian/rootfs"
config="$rootfs/home/dev/.config/regie"
if [[ -n "$client" ]]; then
  file="$config/bridge-tokens/$client"
else
  file="$config/bridge-token"
fi
umask 077
mkdir -p "$(dirname "$file")" "$HOME/.local/state/thor"
exec 8>"$HOME/.local/state/thor/regie-bridge-configure.lock"
flock -w 90 8
# Same token: leave ongoing sessions alone, but ensure the bridge is running.
if [[ -f "$file" && $(cat "$file") == "$token" ]]; then
  chmod 600 "$file"
  unset token
  exec "$HOME/bin/regie-bridge-start"
fi
# The historical token is also the only one a bridge older than ADR 22 reads, and only at its
# start: it is still replaced with the bridge stopped. An app's own token needs no restart, the
# bridge reads the tokens at each connection, so the other apps keep their sessions.
if [[ -z "$client" ]]; then
  "$HOME/bin/regie-bridge-stop"
fi
# Hidden while written: the bridge reads only package names in bridge-tokens/.
tmp=$(mktemp "$(dirname "$file")/.$(basename "$file").XXXXXX")
trap 'rm -f "$tmp"' EXIT
printf '%s' "$token" > "$tmp"
unset token
chmod 600 "$tmp"
mv -f "$tmp" "$file"
"$HOME/bin/regie-bridge-start"
