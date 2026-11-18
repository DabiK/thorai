# shellcheck shell=bash
# Print the sole authorized adb device, or honor an explicit selection. No default console.
select_adb_device() {
  local devices count
  devices=$(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }') || return 1
  if [[ -n "${THOR_SERIAL:-}" ]]; then
    if printf '%s\n' "$devices" | grep -Fxq -- "$THOR_SERIAL"; then
      printf '%s\n' "$THOR_SERIAL"
      return 0
    fi
    echo 'THOR_SERIAL ne désigne pas un appareil adb autorisé.' >&2
    return 1
  fi
  count=$(printf '%s\n' "$devices" | awk 'NF { n++ } END { print n+0 }')
  if [[ "$count" != 1 ]]; then
    echo 'Branche et autorise un appareil adb ; si plusieurs sont branchés, définis THOR_SERIAL.' >&2
    return 1
  fi
  printf '%s\n' "$devices"
}
