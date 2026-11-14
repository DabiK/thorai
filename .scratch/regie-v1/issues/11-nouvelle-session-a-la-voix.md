# 11 — Nouvelle session à la voix

Statut : à fusionner
Bloqué par : 09

## Objectif

Écran 6 de la maquette : un ordre R2 « lance… » devient une fiche pré-remplie qu'on vérifie avant de lancer.

## À livrer

- **Fiche pré-remplie** :
  - agent ;
  - projet, choisi parmi `~/projects` ;
  - branche, existante ou nouvelle avec sa base. Régie ne fait aucune opération git : la branche part dans la consigne et l'agent la gère (décision de l'utilisateur, 13 novembre ; ticket 13) ;
  - niveau de validation ;
  - consigne.
- **Correction de la fiche** : croix haut/bas pour choisir une ligne, gauche/droite pour la changer, L2 pour redicter la consigne.
- **Lancement** : A lance la session et ouvre la session en direct. B annule.
- **Sans R2** : X depuis la vue d'ensemble ouvre la même fiche, vide.

## Critères d'acceptation

- [x] Sur la Thor, trois phrases différentes produisent la bonne fiche, et la session lancée tourne sur la bonne branche. Preuve synthétique locale : corpus 01/02/05, captures 05–07/16/20, journaux session1-events.json, session2-final-events.json, session3-events.json et git-after-new.txt. Lecture humaine restant à vérifier ci-dessous.


## Rapport

- Fiche R2 et X, correction croix/L2, lancement A vers le direct et annulation B livrés.
- Règles dans ReviewNewSession ; catalogue de branches en lecture seule, intention
  Git dans la consigne uniquement. ADR 13 ; aucun sous-agent utilisé.
- Constat bloquant transmis : déjà corrigé dans le socle du ticket 10 (b868609).
  ResyncedExplanationTest réexécuté vert ; mutation ramenant les identifiants locaux
  détectée rouge, fichier restauré avant la porte complète. Aucun correctif à dupliquer.
- Portes : Android 294 tests + lint, pont 271 tests + typage/lint, tous verts.
- Thor : APK réinstallé, WAV Thomas/local et sendevent, trois fiches rejouées,
  correction du niveau, redictée, collision de branche, un/deux écrans ; aucune
  nouvelle session pendant la reprise. Journaux des trois vrais lancements Codex
  précédents relus : main, lecture, travail depuis lecture. Aucun Claude lancé.
- Les quatre Codex d'essai laissés ouverts ont été arrêtés par Quitter, ainsi que le
  pont, à 09:04:35 Paris. Aucune permission, clé, installation tierce ni reboot.
- Relecture : contrôles de resync effectifs ; corrections antérieures des prompts
  de branche, du premier R2, du maintien tactile et du lancement en attente conservées.
- Limites : phrase « essai / modifs auto » mal transcrite et refusée ; le troisième
  succès emploie « travail / tout valider ». Aucun taux de reconnaissance humaine.
- Captures et méthode complète : artifacts/screenshots/11/essai-thor.txt.

### Vérification avec l'utilisateur

1. Ouvrir Régie et accorder le micro si demandé. R2 : « Lance Codex sur test pour
   lire la branche courante ». Relire, puis B.
2. R2 : « Lance Codex sur test, branche lecture, tout valider, pour afficher la
   branche courante ». Corriger le niveau à la croix ; L2 redicte la consigne.
3. A : vérifier le direct, autoriser le changement demandé par Codex, vérifier lecture.
4. R2 : « Lance Codex sur test, nouvelle branche matin depuis lecture, tout valider,
   pour afficher la branche courante ». Employer un nom absent ; vérifier la base,
   A puis autorisation de Codex ; vérifier la branche obtenue.
5. X depuis la régie : fiche vide ; B annule. Éteindre/rallumer physiquement l'écran
   bas pour vérifier le repli. Quitter pour arrêter les sessions.

Statut à fusionner selon la consigne nocturne : tout ce qui ne dépend pas de
l'utilisateur est vert. Le micro réel et l'extinction physique restent à vérifier.

### Relecture après commit

Diff `main...HEAD` relu après 4b884f7, axes architecture et ticket séparés : aucun
nouveau blocage fonctionnel. Preuves sensibles à la mutation resync ; ports/faux,
absence de mutation Git, prompts de base et verrou de création vérifiés. Traces
texte nettoyées uniquement des espaces finaux ; code et tests inchangés depuis les
portes vertes et l'APK essayé. Détail : artifacts/screenshots/11/relecture.txt.
