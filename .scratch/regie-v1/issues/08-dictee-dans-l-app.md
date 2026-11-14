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
Session et refus avec consigne livrés ; cible de commentaire prête pour l'écran du ticket 10.
À fusionner selon la consigne nocturne, avec essais utilisateur explicitement restant à faire.
CRITÈRES:
- [x] Composite build et choix du fournisseur → Android check, capture 23, erreur ElevenLabs capture 24.
- [x] L2, partiels, niveau/durée, relecture et correction → tests domaine/capture ; Thor 10–18, 22, 26.
- [x] Cibles session/refus/diff → DeliverDictationTest ; journal réel seq 2151, 2274 puis 2282 ; diff prêt pour 10.
- [x] Service microphone et permission → manifest, porte Android ; refus de permission réel capture 27.
- [ ] Consigne avec noms de fichiers correcte avec les deux fournisseurs → qualité ASR locale imparfaite avec Thomas ; clé ElevenLabs et vraie voix utilisateur manquantes.
PORTE DE QUALITÉ: Android 210 tests + lint ; voice 127 tests + lint ; bridge 268 tests + typage/lint, verts.
THOR: app.regie seule installée ; WAV Thomas sur vrai moteur local, manette sendevent, maintien tactile ; Codex reçoit et répond ; refus réel vérifié.
REVIEW: avant commit, fin anticipée attend L2, audio libéré sur erreur, maintien tactile et lecture longue corrigés ; relecture finale à suivre dans le journal.
DÉCISIONS PRISES: ADR 09, local/hotwords par défaut, instantané borné du vocabulaire, cible du diff explicite, aucune correction automatique de l'ASR.
POUR L'UTILISATEUR: suivre artifacts/screenshots/08/essai-thor.txt : autoriser micro, dicter local, relire/corriger/envoyer, vérifier service, saisir clé et refaire avec ElevenLabs.
QUESTIONS: aucune décision produit en attente.
CAPTURES: artifacts/screenshots/08/10–29 (voir essai-thor.txt pour les noms exacts).
HORS PÉRIMÈTRE: noms techniques et phrase longue mal reconnus par le moteur local ; contrôleur de permissions Android a planté sur Retour ; droits 05/14 absents. Aucun nouveau Claude.
