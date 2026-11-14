# 08 — Dictée dans l'app

Statut : à fusionner
Bloqué par : 01, 04, 06

## Objectif

Écran 2 de la maquette : dicter avec L2 maintenu, relire, puis envoyer.

## À livrer

- [x] **`voice/` branché en composite build**, avec le fournisseur choisi dans les réglages de l'app. La clé est saisie par l'utilisateur.
- [x] **L2 maintenu** :
  - texte en direct en haut, avec les termes du dépôt mis en avant (vocabulaire tiré des fichiers, fonctions et branches du projet de la session) ;
  - niveau et durée en bas.
- [x] **Au relâchement** :
  - A : envoyer ;
  - B : effacer ;
  - Y : compléter ;
  - X : corriger un mot, à la croix puis en redictant.
- [x] **Cibles de la dictée** : la session affichée, le refus avec consigne (ticket 07), un commentaire de diff (ticket 10).
- [x] **Service au premier plan de type micro** pendant la dictée, si nécessaire.

## Critères d'acceptation

- [ ] Sur la Thor, une consigne dictée en français avec des noms de fichiers arrive à l'agent après relecture, avec chacun des deux fournisseurs.


## Rapport

STATUT: done
RÉSUMÉ: Dictée locale et ElevenLabs intégrées, relecture A/B/Y/X, réglages et vocabulaire du dépôt.
Le panneau un écran conserve désormais le micro tactile et le niveau après Compléter/Corriger.
Ticket à fusionner selon la consigne nocturne ; validation vocale des deux fournisseurs toujours à faire avec l'utilisateur.
CRITÈRES:
- [x] Composite build et choix du fournisseur → Android check ; captures 23–24 du premier passage.
- [x] L2, partiels, niveau/durée et relecture → tests domaine ; Thor 10–18, 26 et 35.
- [x] Micro tactile et niveau en mode un écran → DictationScreenTest rouge puis vert ; Thor 40–46, complément puis correction au toucher injecté.
- [x] Cibles session/refus/diff → DeliverDictationTest ; preuves précédentes seq 2151, 2274, 2282 ; écran diff au ticket 10.
- [x] Service micro protégé contre B/L2 immédiat → ForegroundMicrophoneTest ; annulation en attente et erreur/reprise couvertes.
- [ ] Consigne française avec noms de fichiers reçue après relecture avec chacun des deux fournisseurs → clé ElevenLabs, permission micro et vraie voix utilisateur manquantes ; ASR local imparfait sur WAV Thomas.
PORTE DE QUALITÉ: Android ./gradlew check : 217 tests + lint ; voice ./gradlew check : 127 + lint ; bridge npm run check : 268 + typage/lint, verts.
THOR: app.regie réinstallée ; WAV Thomas sur vrai moteur local, complément au toucher puis correction du mot choisi à la croix ; retour en deux écrans. Brouillon effacé sans envoi, aucun agent lancé, pont arrêté par Quitter à 03:24:21 locale.
REVIEW: main 9fa98afe...df2e237 relu en solo : domaine/adapters, confirmation, tests et captures confrontés au PRD/à la maquette. Défaut compact corrigé, aucune autre anomalie bloquante établie ; preuve des deux fournisseurs toujours manquante et critère décoché.
DÉCISIONS PRISES: micro 58 dp et niveau 32 dp côte à côte dans le panneau compact ; dépendance de test Compose sous le BOM existant ; ADR 09 complété, aucune décision produit nouvelle.
POUR L'UTILISATEUR: suivre artifacts/screenshots/08/essai-thor.txt, étapes 1–9 : autoriser micro, tester local et B/L2, saisir la clé, refaire avec ElevenLabs ; conserver texte relu et message reçu pour chaque fournisseur. Haut Thor non tactile : gestes du panneau compact injectés par adb.
QUESTIONS: aucune décision produit en attente.
CAPTURES: artifacts/screenshots/08/40–47 (noms complets dans essai-thor.txt) ; preuves précédentes conservées.
HORS PÉRIMÈTRE: ASR local imparfait sur noms techniques/phrase longue ; droits 05/14 absents ; ancien affichage « compriscompris » dans le fil. Aucun nouveau Claude.
