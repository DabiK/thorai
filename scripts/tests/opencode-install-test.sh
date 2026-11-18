#!/bin/bash
# Offline installer: synthetic archive, hash verifier, architecture and binary.
set -euo pipefail
root=$(cd "$(dirname "$0")/../.." && pwd)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
mkdir -p "$work/bin" "$work/home" "$work/archive/package/bin"
printf '#!/bin/sh\necho "opencode v2.1.2"\n' > "$work/archive/package/bin/opencode"
chmod +x "$work/archive/package/bin/opencode"
tar -czf "$work/fixture.tgz" -C "$work/archive" package/bin/opencode
cat > "$work/bin/uname" <<'SH'
#!/bin/sh
echo aarch64
SH
cat > "$work/bin/curl" <<'SH'
#!/bin/bash
printf '%s\n' "$*" >> "$OPENCODE_TEST_LOG"
while [[ $# -gt 0 ]]; do
  if [[ "$1" == -o ]]; then cp "$OPENCODE_TEST_ARCHIVE" "$2"; exit; fi
  shift
done
exit 1
SH
cat > "$work/bin/sha512sum" <<'SH'
#!/bin/sh
cat >> "$OPENCODE_TEST_LOG"
exit "${OPENCODE_TEST_HASH_FAILURE:-0}"
SH
chmod +x "$work/bin/"*
export PATH="$work/bin:$PATH" HOME="$work/home" OPENCODE_TEST_LOG="$work/log" OPENCODE_TEST_ARCHIVE="$work/fixture.tgz"
OPENCODE_TEST_HASH_FAILURE=1 bash "$root/thor/opencode/install-binary.sh" && exit 1
[[ ! -e "$HOME/.opencode/bin/opencode" ]]
bash "$root/thor/opencode/install-binary.sh"
bash "$root/thor/opencode/install-binary.sh"
[[ $(grep -c 'https://registry.npmjs.org/@opencode/cli-linux-arm64/-/cli-linux-arm64-2.1.2.tgz' "$work/log") == 2 ]]
[[ $("$HOME/.opencode/bin/opencode" --version) == 'opencode v2.1.2' ]]
[[ ! -e "$HOME/.local/share/opencode" ]]
echo 'official arm64 source, hash rejection, installation and idempotence: OK'
