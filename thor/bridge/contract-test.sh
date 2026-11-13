#!/bin/bash
# Runs the contract tests of the bridge on the Thor, against the real agents: `npm run
# test:contract` in the Debian. On demand only. Uses its own copy of the bridge, with the dev
# dependencies (/home/dev/.local/share/regie/bridge-contract), apart from the installed bridge.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
bridge="$(cd "$here/../../bridge" && pwd)"
# shellcheck source=lib.sh
source "$here/lib.sh"
need_device

dest=/home/dev/.local/share/regie/bridge-contract
deploy_sources "$bridge" "$dest"
deb_sh "set -e; cd '$dest'
lock=\$(sha256sum package-lock.json | cut -d' ' -f1)
if [[ ! -d node_modules || \"\$(cat .installed-lock 2>/dev/null)\" != \"\$lock\" ]]; then
  rm -rf node_modules
  npm ci --no-fund --no-audit --loglevel=error
  echo \"\$lock\" > .installed-lock
fi
npm run test:contract"
