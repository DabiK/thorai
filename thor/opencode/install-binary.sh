#!/bin/bash
# Run as dev in Debian. Official prebuilt glibc arm64 executable, pinned and verified.
set -euo pipefail
[[ $(uname -m) == aarch64 ]] || { echo 'Opencode : Debian arm64 requise.' >&2; exit 1; }
binary="$HOME/.opencode/bin/opencode"
profile_line='export PATH="$HOME/.opencode/bin:$PATH"'
touch "$HOME/.profile"
grep -Fxq "$profile_line" "$HOME/.profile" || printf '\n%s\n' "$profile_line" >> "$HOME/.profile"
if [[ -x "$binary" && $("$binary" --version </dev/null) == 'opencode v2.1.2' ]]; then
  echo 'opencode 2.1.2 déjà installé (aucune connexion modifiée).'
  exit 0
fi
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
curl --fail --location --retry 2 --silent --show-error \
  https://registry.npmjs.org/@opencode/cli-linux-arm64/-/cli-linux-arm64-2.1.2.tgz -o "$work/opencode.tgz" </dev/null
# npm dist.integrity of @opencode/cli-linux-arm64@2.1.2; official packages/cli build.
expected='dbde0803d4b5b17380cce0513cc72716e6295e515e51a2939694e4d1aed332fb9cedc25b7d2e76a684f0dc0948bbbc9c6e7f131a64e68f6ece3b1b0c98108b12'
printf '%s  %s\n' "$expected" "$work/opencode.tgz" | sha512sum -c -
tar -xzf "$work/opencode.tgz" -C "$work" package/bin/opencode
[[ $("$work/package/bin/opencode" --version </dev/null) == 'opencode v2.1.2' ]]
mkdir -p "$(dirname "$binary")"
install -m 755 "$work/package/bin/opencode" "$binary.tmp"
mv -f "$binary.tmp" "$binary"
echo 'opencode 2.1.2 installé ; connexion Go à faire dans ThorAI.'
