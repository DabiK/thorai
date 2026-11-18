#!/usr/bin/env bash
# Offline checks only: no console, network or agent.
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
for test in "$root"/scripts/tests/*-test.sh; do bash "$test"; done
for test in "$root"/scripts/tests/*-test.py; do python3 "$test"; done
for script in "$root"/thor/bridge/*.sh "$root"/thor/computer-use/*.sh "$root"/thor/opencode/*.sh "$root"/thor/install-on-device.sh "$root"/scripts/release.sh; do bash -n "$script"; done
