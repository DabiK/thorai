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

STATUT: done
RÉSUMÉ: Diff par fichier, navigation croix/stick, commentaire L2 contextualisé et explication Y/A écrite livrés.
L’explication en file ignore désormais la réponse du tour précédent et suit sa propre fin. Statut : à fusionner.
CRITÈRES:
- [x] Diff réel, commentaire vocal et prise en compte → essai Thomas/local/Codex, seuil.ts passé de 70 à 80 (after-comment.json, captures 04–09).
- [x] Explication liée à son tour → QueuedExplanationReviewTest et ThreadBuilderTest, rouges puis verts ; deux messages en file et réponse suivante couverts.
- [x] Vérifications humaines explicites → pas à pas ci-dessous.
PORTE DE QUALITÉ: cd android && ./gradlew check vert ; 271 tests, aucun échec/ignoré, lint vert (queued-android-check.txt).
THOR: APK réinstallé ; même Codex repris, Y/A pendant sleep 25, attente puis réponse écrite en un/deux écrans (39–42).
Codex a accepté en steering : queued exact vérifié par tests, pas reproduit sur console. Quitter a arrêté Codex/pont à 05:03:51 locale.
REVIEW: Réponse précédente exclue, fin indépendante du statut global ; métadonnées de fin bornées après éviction. Diff main...a2a2cbc relu ; aucun autre blocage, git diff --check vert. Détails dans essai-thor.txt.
DÉCISIONS PRISES: ADR 12 complété : identité des réponses décodée depuis delivery et les fins de tour, sélection dans le domaine. Aucun nouveau protocole ni bibliothèque.
POUR L’UTILISATEUR:
1. Ouvrir Régie ; accorder si nécessaire RUN_COMMAND et le microphone via Réglages voix. Aucun droit accordé cette nuit.
2. Ouvrir la session regie-diff10-20261114 puis X ; choisir seuil.ts avec ←→, une ligne avec ↑↓, essayer le stick droit.
3. Maintenir physiquement L2, dire « Ajoute un test pour cette valeur », relâcher ; relire texte, fichier, ligne et côté supprimé éventuel.
4. B efface ; A envoie. Vérifier la réponse de Codex et l’édition réelle dans le fil/diff.
5. Y prépare une explication sans envoi ; A demande. Lire la réponse, défiler, puis B. Aucun son attendu.
6. Éteindre physiquement l’écran inférieur via l’assistant Odin : vérifier le panneau sur l’écran haut, puis rallumer et vérifier son retour. Quitter Régie.
QUESTIONS: aucune décision produit en attente.
CAPTURES: artifacts/screenshots/10/ : 01–04, 07–11, 13 ; identity-30–34 ; queued-38–42 (42-haut uniquement).
HORS PÉRIMÈTRE: WrittenSummary.of (ticket 12) utilise encore le dernier message par position : même risque pour les bilans queued, relevé statiquement, non corrigé ici. Permissions/redémarrage des tickets précédents inchangés.

Détails des passages et preuves : artifacts/screenshots/10/essai-thor.txt.
