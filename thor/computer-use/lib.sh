# shellcheck shell=bash
# Fonctions communes aux scripts de thor/computer-use lancés depuis le Mac.
# À sourcer, pas à exécuter.

if [[ "${REGIE_ON_DEVICE:-0}" != 1 ]]; then
  source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/../../scripts/device.sh"
fi
THOR_SERIAL=${THOR_SERIAL:-}
TERMUX_PREFIX=/data/data/com.termux/files/usr
TERMUX_HOME=/data/data/com.termux/files/home

# Socket Unix privée du serveur adb de la Debian (même valeur que start.sh),
# son dossier, et la ligne qui signe la cale ~dev/.local/bin/adb (adb-wrapper).
# shellcheck disable=SC2034
ADB_SOCKET=localfilesystem:/home/dev/.thor-adb/server
# shellcheck disable=SC2034
ADB_SOCKET_DIR=/home/dev/.thor-adb
# shellcheck disable=SC2034
ADB_WRAPPER_TAG='thor-android : adb des shells de dev'

# Le script lu par « bash -s » partage l'entrée standard des commandes qu'il
# lance : une commande qui lit stdin (adb shell sans -n, par exemple) avalerait
# la suite du script, qui ne s'exécuterait pas. On l'enveloppe donc dans un
# bloc { … } : bash le lit en entier avant d'exécuter quoi que ce soit, et
# l'entrée standard est vide quand les commandes tournent.
_whole_script() {
  printf '{\n'
  cat
  printf '\n}\n'
}

# Exécute dans Termux le script bash lu sur l'entrée standard.
termux_sh() {
  if [[ "${REGIE_ON_DEVICE:-0}" == 1 ]]; then
    _whole_script | bash -s
    return
  fi
  _whole_script | adb -s "$THOR_SERIAL" shell -T run-as com.termux \
    "$TERMUX_PREFIX/bin/env" HOME="$TERMUX_HOME" PREFIX="$TERMUX_PREFIX" \
    TMPDIR="$TERMUX_PREFIX/tmp" PATH="$TERMUX_PREFIX/bin:/system/bin" \
    LD_PRELOAD="$TERMUX_PREFIX/lib/libtermux-exec.so" LANG=en_US.UTF-8 \
    "$TERMUX_PREFIX/bin/bash" -s
}

# Exécute dans la Debian, sous l'utilisateur $1 (dev ou root), le script bash
# lu sur l'entrée standard. Mêmes montages que le lanceur dev-linux de Termux.
debian_sh() {
  if [[ "${REGIE_ON_DEVICE:-0}" == 1 ]]; then
    _whole_script | proot-distro login debian --user "$1" \
      --bind "$HOME/projects:/home/dev/projects" --bind "$HOME/.codex:/home/dev/.codex" \
      -- /bin/bash -s
    return
  fi
  _whole_script | adb -s "$THOR_SERIAL" shell -T run-as com.termux \
    "$TERMUX_PREFIX/bin/env" HOME="$TERMUX_HOME" PREFIX="$TERMUX_PREFIX" \
    TMPDIR="$TERMUX_PREFIX/tmp" PATH="$TERMUX_PREFIX/bin:/system/bin" \
    LD_PRELOAD="$TERMUX_PREFIX/lib/libtermux-exec.so" LANG=en_US.UTF-8 \
    "$TERMUX_PREFIX/bin/proot-distro" login debian --user "$1" \
    --bind "$TERMUX_HOME/projects:/home/dev/projects" \
    --bind "$TERMUX_HOME/.codex:/home/dev/.codex" \
    -- /bin/bash -s
}

# Écrit sur la sortie standard les commandes bash qui recréent le fichier
# local $1 en $2 sur la console, avec les droits $3.
emit_file() {
  local src=$1 dest=$2 mode=$3
  printf 'mkdir -p %q\n' "$(dirname "$dest")"
  printf 'base64 -d > %q <<"__THOR_B64__"\n' "$dest"
  base64 < "$src" | tr -d '\n'
  printf '\n__THOR_B64__\n'
  printf 'chmod %s %q\n' "$mode" "$dest"
}

require_console() {
  [[ "${REGIE_ON_DEVICE:-0}" == 1 ]] && return 0
  THOR_SERIAL=$(select_adb_device) || exit 1
  if ! adb -s "$THOR_SERIAL" get-state >/dev/null 2>&1; then
    echo "La console $THOR_SERIAL n'est pas visible par adb depuis le Mac." >&2
    exit 1
  fi
}

# Programme awk qui retire d'un config.toml de Codex la section
# [mcp_servers.thor_android] et ses sous-tables, jusqu'à la prochaine ligne « [ ».
# Les commentaires et lignes vides collés à la section suivante sont gardés.
# Les lignes vides juste avant la section retirée partent avec elle.
# shellcheck disable=SC2034,SC2016
TOML_DROP_THOR_ANDROID='
/^[ \t]*\[/ {
  if ($0 ~ /^[ \t]*\[mcp_servers\.thor_android[].]/) { buf = ""; skip = 1; next }
  if (skip) { skip = 0 }
  printf "%s", buf; buf = ""; print; next
}
skip {
  if ($0 ~ /^[ \t]*(#.*)?$/) { buf = buf $0 "\n" } else { buf = "" }
  next
}
/^[ \t]*$/ { buf = buf $0 "\n"; next }
{ printf "%s", buf; buf = ""; print }
END { if (!skip) printf "%s", buf }
'

# Commandes bash (à exécuter dans Termux) qui définissent drop_thor_android :
# applique le programme ci-dessus à $config, par un fichier temporaire et un mv.
emit_toml_helpers() {
  printf 'TOML_DROP_THOR_ANDROID=%q\n' "$TOML_DROP_THOR_ANDROID"
  cat <<'EOF2'
drop_thor_android() {
  local tmp
  tmp=$(mktemp "$config.XXXXXX")
  awk "$TOML_DROP_THOR_ANDROID" "$config" > "$tmp"
  chmod --reference="$config" "$tmp"
  mv -f "$tmp" "$config"
}
EOF2
}
