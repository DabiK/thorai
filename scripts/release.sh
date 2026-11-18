#!/usr/bin/env bash
# Default publishes. --build-only prepares/verifies local artifacts without contacting GitHub.
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
version=${1:?usage: scripts/release.sh <major.minor.patch> [--build-only]}
[[ "$version" =~ ^(0|[1-9][0-9]{0,2})\.(0|[1-9][0-9]{0,2})\.(0|[1-9][0-9]{0,2})$ ]] || { echo 'Version attendue : major.minor.patch (0 à 999).' >&2; exit 1; }
[[ $# == 1 || ( $# == 2 && "$2" == --build-only ) ]] || { echo 'Option inconnue.' >&2; exit 1; }
major=${BASH_REMATCH[1]} minor=${BASH_REMATCH[2]} patch=${BASH_REMATCH[3]}
code=$((major * 1000000 + minor * 1000 + patch + 1))
config=${REGIE_RELEASE_CONFIG:-$HOME/.config/regie/release.properties}
python3 "$root/scripts/release-key.py" "$config" "$root"
sdk=${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}
signer=${APKSIGNER:-}
if [[ -z "$signer" ]]; then
  if command -v apksigner >/dev/null 2>&1; then signer=apksigner
  else signer=$(find "$sdk/build-tools" -name apksigner -type f | sort | tail -1); fi
fi
[[ -n "$signer" ]] || { echo 'apksigner absent : installer les build-tools Android.' >&2; exit 1; }
(cd "$root/android" && REGIE_RELEASE_CONFIG="$config" ./gradlew :app:assembleRelease "-PregieVersionName=$version" "-PregieVersionCode=$code")
output="$root/android/app/build/outputs/apk/release"
"$signer" verify "$output/app-release.apk"
apk="regie-$version.apk"
cp "$output/app-release.apk" "$output/$apk"
(cd "$output" && shasum -a 256 "$apk" > "$apk.sha256")
printf 'APK signé et SHA-256 : %s/%s\n' "$output" "$apk"
if [[ "${2:-}" == --build-only ]]; then exit 0; fi
# The orchestrator runs this only after merging. Pin the actual checked-out commit.
cd "$root"
target=$(git rev-parse HEAD)
gh release create "v$version" "$output/$apk" "$output/$apk.sha256" \
  --target "$target" --title "Régie $version" --notes-file "$root/README.md"
