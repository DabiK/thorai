# 23 — Se connecter à Claude et à Codex depuis la console

Statut : en attente (après 22)
Possède : `bridge/`, `android/` (écran de connexion)

## Retour de l'utilisateur, 18 novembre

« Le mécanisme d'authentification de Claude ou de Codex, c'est toi qui l'as configuré. Si l'application est livrée seule, une personne doit pouvoir se connecter directement depuis la console, je pense par device code. »

## À livrer

- **Le pont expose, pour chaque agent** : état de connexion (connecté ou non, compte si l'agent le donne sans secret), lancement d'une connexion, déconnexion.
- **Connexion par code d'appareil** si l'agent le propose :
  - Codex : `codex login --device-auth`.
  - Claude Code : vérifier dans sa documentation à jour s'il existe un flux par code ou par URL à ouvrir sur un autre appareil, en mode sans interface. Sinon, trouver l'équivalent le plus simple et l'écrire dans l'ADR.
- **L'app** montre l'URL et le code en grand, avec un QR code à scanner au téléphone, puis le résultat. La connexion se fait depuis l'accueil ou les réglages, et elle est proposée au premier lancement si un agent n'est pas connecté.
- **Aucun secret** ne transite par l'app ni par les journaux : les jetons restent dans les fichiers des agents.

## Critères d'acceptation

- Sur la Thor, une déconnexion de Codex depuis Régie, puis une reconnexion par code d'appareil, se font sans terminal ni Mac.
- Même chose pour Claude, si son CLI le permet. Sinon, la limite est écrite et la marche manuelle est indiquée dans l'app.
