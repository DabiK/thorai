# 21 — Passe sur les textes et l'interface : moins de mots, les bons libellés, une seule voix

Statut : prêt
Bloqué par : 20
Possède : `android/` (textes et composants d'interface), `README.md` (captures et libellés cités)

## Objectif

Demande de l'utilisateur, le 14 novembre : retirer le « slop » des textes, harmoniser l'interface, mettre les bons libellés là où il faut, et retirer le texte non nécessaire.

## À livrer

- **Inventaire de tous les textes visibles** : `strings.xml` et textes en dur. Pour chacun : le garder, le raccourcir, le renommer ou le supprimer. La liste avant/après va dans le rapport.
- **Règles d'écriture**, notées dans un court guide `docs/textes.md` :
  - tutoiement ;
  - phrases de 8 mots au plus sur les écrans ;
  - un verbe d'action par bouton (« Envoyer », « Autoriser », « Refuser ») ;
  - pas de jargon technique quand un mot simple existe, pas de formule creuse ou enjouée, pas d'emoji ;
  - pas de texte qui répète ce que l'écran montre déjà ;
  - typographie française (espaces insécables avant « : ? ! » et dans les guillemets).
- **Libellés cohérents partout.**
  - Un même concept porte le même nom sur tous les écrans : session, validation, consigne, projet, mode, modèle, réflexion.
  - Les puces de boutons de la manette (A, B, X, Y, L1, R1, L2, R2, Start) ont le même style et le même ordre partout.
- **Harmonisation visuelle** :
  - espacements, tailles de texte, alignements, états vides, états d'erreur et de chargement ;
  - les deux écrans suivent les mêmes règles ;
  - références : le système visuel de la maquette et l'ADR 05.
- **Texte non nécessaire** : retirer les aides permanentes devenues inutiles, les doublons, et les mentions de debug visibles en build normal.
- **README** : mettre à jour les captures et les libellés qu'il cite.

## Critères d'acceptation

- Captures avant et après de chaque écran, sur l'émulateur, dans `artifacts/screenshots/21/`.
- Aucun texte visible ne contredit le guide `docs/textes.md`. Les tests qui vérifient des libellés sont mis à jour, pas supprimés.
- Les portes de qualité sont vertes.
