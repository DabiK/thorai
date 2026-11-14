# 10 — Relecture du diff

Statut : à fusionner
Bloqué par : 06, 08

## Objectif

Écran 5 de la maquette : lire les changements d'une session et les commenter à la voix.

## À livrer

- **En haut** : le diff par fichier, ligne par ligne à la croix, défilement au stick droit, coloration simple.
- **En bas** : la liste des fichiers (+/−).
- **Commentaire dicté** sur la ligne sélectionnée (L2), envoyé à l'agent avec le fichier et la ligne.
- **Y : « explique-moi ce bloc »**, demandé à l'agent, avec la réponse affichée à l'écran (pas de lecture à voix haute, décision de l'utilisateur du 14 novembre).

## Critères d'acceptation

- [x] Sur la Thor, on relit le diff d'une vraie session, on commente une ligne à la voix, et l'agent en tient compte.


## Rapport

- Écran de diff livré : X, fichiers +/−, croix, stick, L2 et Y puis A, tactile et
  mode un/deux écrans. Domaine derrière AgentGateway ; aucune règle d’envoi dans l’UI.
- Critère prouvé avec voix synthétique WAV Thomas sur le vrai moteur local et un
  vrai Codex : commentaire seuil.ts ligne 1 reçu une fois, fichier passé de 70 à 80,
  explication écrite revenue. Vrai microphone et extinction physique restent à
  vérifier avec l’utilisateur, selon le pas à pas de `artifacts/screenshots/10/essai-thor.txt`.
- Android final : 265 tests + lint verts (`review-android-check.txt`), tests d’abord (`red-*`).
- ADR 12 : éditions historiques par fichier, pas de diff Git inventé ; numéros
  ancien/nouveau, coordonnées d’extrait signalées quand l’agent ne donne pas leur position.
- Un Codex d’essai, aucun Claude. Quitter a arrêté l’agent et le pont (stopped.json).
  Pas de permission accordée, reboot, push ou fusion. Seul app.regie installé.
- Captures utiles : 01–04, 07–11 et 13 ; journaux avant/après et fichier final conservés.
- Relecture solo après bec880e : sélection hors champ au bord, arrêt du stick pendant
  les réglages et Markdown de l’explication corrigés ; quatre régressions rouges puis
  vertes, attendu littéral renforcé. Réinstallation et essais Thor 15–16, 21–28 ; même
  Codex repris puis arrêté, aucun autre agent. Le pas à pas humain reste applicable.
