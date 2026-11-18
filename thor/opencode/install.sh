#!/bin/bash
# From the Mac (adb) or Termux (REGIE_ON_DEVICE=1). Never authenticates OpenCode.
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
source "$here/../computer-use/lib.sh"
require_console
debian_sh dev < "$here/install-binary.sh"
