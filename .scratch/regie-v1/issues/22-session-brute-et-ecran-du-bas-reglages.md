# 22 — Session brute comme une app de bureau, écran du bas en table de réglages, accueil en liste

Statut : prêt
Possède : `android/` (interface)

## Retours de l'utilisateur, 18 novembre (ses mots, résumés)

1. **Retirer toutes les métadonnées de Régie dans le fil** (« Point Régie », « Question », « Aucune suite », « test », « fait », etc.). « L'idée, c'est de répliquer au maximum ce que le harness répond, de façon brute. Pas de métadonnées par-dessus. Je dois me sentir comme dans une application de bureau. »
2. **L'écran du bas d'une session devient une table de réglages du harness**, et non plus une liste de raccourcis.
   - Modèle, niveau de réflexion, mode rapide (fast mode) s'il existe, mode de validation : **toutes les options que l'agent expose** (options de configuration ACP du ticket 17), présentées de façon générique.
   - Le raccourci Y « faire le point » disparaît, puisqu'il est lié au point Régie retiré.
   - Garder L2 (parler), B (retour), X (modifications), L1/R1 et Start.
3. **Dictée (L2)** : l'écran du haut garde toujours le fil, il ne change jamais. **C'est l'écran du bas qui affiche la transcription en direct** pendant que L2 est maintenu.
   - Une fois le message envoyé, retour automatique à la table de réglages, sans écran « message envoyé, B pour revenir ».
   - La relecture avant envoi reste possible sur l'écran du bas (A envoie, B efface), sans changer l'écran du haut.
4. **Accueil en liste**, et non plus en grille de 4 cartes. Une ligne par session, défilable : agent, projet, branche, état (en cours, en attente de consigne, validation demandée…), dernière action, ancienneté. Toutes les informations des cartes, en plus compact.

## Contraintes

- Système visuel de Régie (ADR 05, `docs/textes.md`), sobre, dense, lisible à distance de bras sur un écran de 6 pouces.
- Mettre à jour les tests touchés, pas les supprimer. `./gradlew check` doit être vert.
- Captures avant et après des deux écrans dans `artifacts/screenshots/22/` : sur la Thor si elle est branchée (`scripts/thor-install.sh`, `scripts/thor-shot.sh`), sinon sur l'émulateur (`docs/emulateur.md`, pont du Mac, Codex comme agent).

## Critères d'acceptation

- Le fil ne montre que ce que l'agent produit : messages, outils, sorties, diffs, demandes de validation.
- Pendant une session, on change le modèle ou la réflexion depuis l'écran du bas, sans changer de page.
- L2 → la transcription s'affiche en bas, le haut ne bouge pas ; après A, retour seul aux réglages.
- L'accueil est une liste.
