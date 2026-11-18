#!/bin/bash
# Installs the Régie bridge on the Thor, from the Mac. Idempotent: run it again after any change.
#
#   thor/bridge/install.sh            everything
#   thor/bridge/install.sh token-app  only copies the token to the app (once the app is installed)
#
# What it does:
# 1. copies bridge/ into the Debian (/home/dev/.local/share/regie/bridge) and runs `npm ci`;
# 2. creates the token once (32 random bytes), for the bridge and for the app (run-as);
# 3. sets allow-external-apps=true in Termux, for RUN_COMMAND;
# 4. installs regie-bridge-start, -stop, -supervise and regie-android-access in the ~/bin of Termux.
# Nothing starts at boot. The agents' settings (~/.claude, ~/.codex) are not touched.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
bridge="$(cd "$here/../../bridge" && pwd)"
# shellcheck source=lib.sh
source "$here/lib.sh"
need_device

copy_token_to_app() {
  if [[ "${REGIE_ON_DEVICE:-0}" == 1 ]]; then
    echo "Régie établira son jeton à l'ouverture, par RUN_COMMAND."
    return 0
  fi
  if ! adb -s "$SERIAL" shell "run-as $APP_ID true" > /dev/null 2>&1; then
    cat >&2 << EOF
L'app $APP_ID n'est pas installée (ou est en release) : copie debug ignorée.
Une app de release établit son jeton elle-même à l'ouverture.
Une fois l'app installée, relancer seulement cette étape :
  thor/bridge/install.sh token-app
EOF
    return 0
  fi
  # The token goes from Termux to the app through this pipe: never on disk, never printed.
  termux_sh "cat '$ROOTFS$DEB_TOKEN'" |
    adb -s "$SERIAL" shell "run-as $APP_ID sh -c 'umask 077; mkdir -p files && \
cat > $APP_TOKEN.tmp && mv $APP_TOKEN.tmp $APP_TOKEN'"
  echo "Jeton copié dans l'app ($APP_ID, $APP_TOKEN)."
}

if [[ "${1:-}" == "token-app" ]]; then
  copy_token_to_app
  exit 0
fi

bash "$here/../opencode/install.sh"

echo "1/4 Pont : copie dans la Debian et dépendances"
deploy_sources "$bridge" "$DEB_APP"
# npm ci only when the lock file changed. No dev dependency, no optional one: the agent binaries
# embedded in the adapters (~740 Mo) are not used, the console's claude and codex are.
deb_sh "set -e; cd '$DEB_APP'
lock=\$(sha256sum package-lock.json | cut -d' ' -f1)
if [[ ! -d node_modules || \"\$(cat .installed-lock 2>/dev/null)\" != \"\$lock\" ]]; then
  rm -rf node_modules
  npm ci --omit=dev --omit=optional --no-fund --no-audit --loglevel=error
  echo \"\$lock\" > .installed-lock
fi
echo \"   node_modules : \$(du -sh node_modules | cut -f1)\""

echo "2/4 Jeton"
termux_sh "set -e; umask 077; f='$ROOTFS$DEB_TOKEN'; mkdir -p \"\$(dirname \"\$f\")\"
if [[ ! -s \"\$f\" ]]; then
  head -c 32 /dev/urandom | od -An -tx1 | tr -d ' \n' > \"\$f.tmp\" && mv \"\$f.tmp\" \"\$f\"
  echo '   jeton créé'
else
  echo '   jeton existant gardé'
fi
chmod 600 \"\$f\""
copy_token_to_app

echo "3/4 Termux : allow-external-apps=true (RUN_COMMAND)"
termux_sh "set -e; f=\"\$HOME/.termux/termux.properties\"; mkdir -p \"\$HOME/.termux\"; touch \"\$f\"
if grep -qE '^[[:space:]]*#?[[:space:]]*allow-external-apps[[:space:]]*=' \"\$f\"; then
  sed -i -E 's/^[[:space:]]*#?[[:space:]]*allow-external-apps[[:space:]]*=.*/allow-external-apps=true/' \"\$f\"
else
  printf '\nallow-external-apps=true\n' >> \"\$f\"
fi
termux-reload-settings
grep '^allow-external-apps' \"\$f\""

echo "4/4 Scripts de démarrage et d'arrêt"
# Written aside then renamed: a running copy of the script is not changed under its feet.
install_script() { # install_script <source in thor/bridge> <name in ~/bin>
  termux_sh "set -e; mkdir -p \"\$HOME/bin\"; f=\"\$HOME/bin/$2\"; \
cat > \"\$f.tmp\"; chmod 700 \"\$f.tmp\"; mv -f \"\$f.tmp\" \"\$f\"" < "$here/$1"
}
for name in start stop supervise configure; do
  install_script "$name.sh" "regie-bridge-$name"
done
# The agents' access to Android after a restart (ticket 03), run by regie-bridge-start.
install_script android-access.sh regie-android-access
echo "   ~/bin/regie-bridge-configure, regie-bridge-start, regie-bridge-stop, regie-bridge-supervise, regie-android-access"

cat << EOF

Installé. Démarrer et arrêter le pont par RUN_COMMAND, comme le fera l'app :
  thor/bridge/run-command.sh start
  thor/bridge/run-command.sh stop
L'intent part avec l'uid de Termux (son am) : « adb shell am startservice … » est refusé,
l'utilisateur shell n'a pas la permission com.termux.permission.RUN_COMMAND.

Pour que Régie rallume le débogage sans fil après un redémarrage (accès des agents à Android),
une seule fois, l'app installée :
  adb -s $SERIAL shell pm grant $APP_ID android.permission.WRITE_SECURE_SETTINGS
EOF
