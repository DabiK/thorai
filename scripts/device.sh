# shellcheck shell=bash
# Print the sole authorized adb device, or honor an explicit selection. No default console.
select_adb_device() {
  local listing devices count
  listing=$(adb devices) || return 1
  devices=$(printf '%s\n' "$listing" | awk 'NR > 1 && $2 == "device" { print $1 }') || return 1
  if [[ -n "${THOR_SERIAL:-}" ]]; then
    if printf '%s\n' "$devices" | grep -Fxq -- "$THOR_SERIAL"; then
      printf '%s\n' "$THOR_SERIAL"
      return 0
    fi
    echo 'THOR_SERIAL ne désigne pas un appareil adb autorisé.' >&2
    return 1
  fi
  count=$(printf '%s\n' "$listing" | awk 'NR > 1 && NF >= 2 { n++ } END { print n+0 }')
  if [[ "$count" != 1 || -z "$devices" ]]; then
    echo 'Branche et autorise un appareil adb ; si plusieurs sont branchés, définis THOR_SERIAL.' >&2
    return 1
  fi
  printf '%s\n' "$devices"
}

# Print the package of the app built by this repository: applicationId, plus the
# applicationIdSuffix of the build types (ticket 50: the game version installs next to the
# classic app, never over it). REGIE_APP_ID overrides it, e.g. to target the classic app.
regie_app_id() {
  if [[ -n "${REGIE_APP_ID:-}" ]]; then
    printf '%s\n' "$REGIE_APP_ID"
    return 0
  fi
  local gradle id suffix
  gradle="${1:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/android/app/build.gradle.kts}"
  id=$(sed -nE 's/^[[:space:]]*applicationId[[:space:]]*=[[:space:]]*"([^"]+)".*/\1/p' "$gradle" | head -n 1)
  suffix=$(sed -nE 's/.*applicationIdSuffix[[:space:]]*=[[:space:]]*"([^"]+)".*/\1/p' "$gradle" | head -n 1)
  if [[ -z "$id" ]]; then
    echo "applicationId introuvable dans $gradle." >&2
    return 1
  fi
  printf '%s%s\n' "$id" "$suffix"
}
