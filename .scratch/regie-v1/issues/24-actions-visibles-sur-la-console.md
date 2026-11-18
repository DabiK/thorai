# 24 — Les agents agissent de façon visible sur la console

Statut : prêt
Possède : `thor/computer-use/` (consignes, installation)

## Retour de l'utilisateur, 18 novembre

Il a demandé à l'agent d'ouvrir le navigateur. L'agent a appelé `thor_browser` (`browser_navigate`), mais rien n'a changé à l'écran.

## Cause probable

`thor_browser`, c'est Playwright avec un Chromium sans fenêtre dans la Debian : il est invisible par nature. Pour agir sur ce que l'utilisateur voit, l'agent doit passer par `thor_android` (mobile-mcp : ouvrir une appli Android comme Chrome, toucher, saisir) ou par `thor-screen`.

## À livrer

- **Consignes globales** (`thor/computer-use/consignes.md`, posées par `install.sh`) :
  - quand l'utilisateur parle de la console, de l'écran, d'une appli ou « du navigateur », l'agent agit sur Android avec `thor_android` ou `thor-screen`, de façon visible ;
  - `thor_browser` est réservé à l'automatisation web invisible (tests, extraction), et l'agent dit qu'il est invisible quand il s'en sert ;
  - indiquer comment ouvrir une URL dans le navigateur Android : intent `VIEW`, appli par défaut.
- **Essayer si `mobile-mcp` peut ouvrir directement une URL** dans le navigateur Android (outil d'ouverture d'URL ou `am start -a android.intent.action.VIEW -d <url>`), et le documenter.
- **Réinstallation sur la Thor** (`thor/computer-use/install.sh`) si elle est branchée. Sinon, laisser la marche à suivre dans le rapport.

## Critères d'acceptation

- Sur la Thor, depuis une session Codex lancée par Régie, « ouvre le navigateur sur wikipedia.org » ouvre la page dans le navigateur Android, visible à l'écran.
