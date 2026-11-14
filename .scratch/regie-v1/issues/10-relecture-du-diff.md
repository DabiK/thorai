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
RÉSUMÉ: La sélection survit à la resynchronisation du journal grâce à l’identité persistante de l’édition.
L’explication termine son attente après l’arrêt connu de l’agent, avec erreur et texte partiel conservé. Statut : à fusionner.
CRITÈRES:
- [x] Diff réel, commentaire vocal et prise en compte → essai Thomas/local/Codex historique, seuil.ts de 70 à 80 (after-comment.json, 04–09).
- [x] Sélection conservée au resync → DiffRecoveryTest rouge puis vert ; mutation vers l’identité locale détectée (recovery-red-identity.txt).
- [x] Crash sans fin de tour → DiffRecoveryTest : erreur et réponse partielle, queued sans réponse, rechargement et absence de faux arrêt.
- [x] Explication liée à sa demande persistante → QueuedExplanationReviewTest et ResyncedExplanationTest restent verts.
PORTE DE QUALITÉ: cd android && ./gradlew check vert ; 276 tests, aucun échec/ignoré, lint vert (recovery-android-check.txt).
THOR: APK réinstallé ; même Codex repris, Y/A ligne 2, adapter arrêté après le début de réponse ; texte partiel/erreur visibles en un/deux écrans (56–57).
Quitter a fermé app/pont à 05:29:56 ; aucun agent d’essai restant. Cet arrêt émet session_error : crash sans fin explicite et resync exact prouvés par tests, pas sur console. Aucun Claude ni permission accordée.
REVIEW: Deux constats reproduits puis corrigés ; l’identité locale seule ne suffit pas, le test de mutation le vérifie. Relecture finale après commit à consigner.
DÉCISIONS PRISES: ADR 12 complété : identité ACP persistante, sélection conservée et actions suspendues pendant chargement, fin sur arrêt connu.
POUR L’UTILISATEUR:
1. Ouvrir Régie ; accorder si nécessaire RUN_COMMAND et le microphone via Réglages voix. Aucun droit accordé cette nuit.
2. Ouvrir regie-diff10-20261114 puis X ; choisir seuil.ts avec ←→, une ligne avec ↑↓, essayer le stick droit.
3. Maintenir physiquement L2, dire « Ajoute un test pour cette valeur », relâcher ; relire texte, fichier, ligne et côté supprimé éventuel.
4. B efface ; A envoie. Vérifier la réponse de Codex et l’édition réelle dans le fil/diff.
5. Y prépare une explication sans envoi ; A demande. Lire la réponse, défiler, puis B. Aucun son attendu.
6. Éteindre physiquement l’écran inférieur via l’assistant Odin : vérifier le panneau sur l’écran haut, puis rallumer et vérifier son retour. Quitter Régie.
QUESTIONS: aucune décision produit en attente.
CAPTURES: artifacts/screenshots/10/recovery-54–58 ; preuve vocale historique 04–09.
HORS PÉRIMÈTRE: WrittenSummary.of (ticket 12) utilise encore le dernier message par position : risque pour les bilans queued déjà signalé, non corrigé ici.

Détails des passages et preuves : artifacts/screenshots/10/essai-thor.txt.
