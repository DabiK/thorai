# 15 — Nouveau projet depuis Régie, liste des projets à jour, barre d'état

Statut : à fusionner
Bloqué par : rien (tickets 01 à 14 fusionnés)
Possède : `android/`, `bridge/`, `docs/protocole-pont.md`

## Objectif

Retours de l'utilisateur au premier essai réel, le 14 novembre.

1. **« J'ai pas la possibilité de choisir un projet. »** La fiche Nouvelle session a été ouverte avant que Régie soit connectée au pont, et la liste des projets est restée vide.
2. **« J'aimerais pouvoir lancer un nouveau projet, pas seulement une liste prédéfinie. »**
3. **La barre d'état d'Android** (l'heure, les icônes) chevauche le titre en haut de l'écran du haut.

## À livrer

1. **Liste des projets toujours à jour.**
   - La fiche (ligne « Projet ») charge `projects.list` à l'ouverture, puis se recharge dès que la connexion au pont s'établit ou revient.
   - Pendant le chargement, ou si le pont est injoignable, la ligne le dit sobrement, au lieu d'un « À choisir » inerte.
   - Gauche et droite font défiler les projets.
2. **Nouveau projet.**
   - La ligne « Projet » propose, en dernier choix, « Nouveau projet… ». Le nom se dicte avec L2, ou est tiré de l'ordre vocal R2 (« lance Codex sur un nouveau projet qui s'appelle météo »).
   - Le nom est normalisé en dossier (minuscules, tirets).
   - Le pont crée `~/projects/<nom>` avec `git init` et une branche `main`, sans autre fichier. L'agent fait le reste à partir de la consigne. C'est une nouvelle méthode, ou une option de `session.create` (par exemple `newProject: true`).
   - Refus clair si le dossier existe déjà ou si le nom est vide ou invalide.
   - Le protocole et l'ADR 02 sont mis à jour.
3. **Barre d'état** : rien ne doit plus chevaucher le contenu de l'écran du haut. Soit Régie passe en plein écran immersif, soit elle respecte les marges système (insets). Le choix est noté dans l'ADR 05. Vérifier aussi l'écran du bas.

## Critères d'acceptation

- [x] Sur la Thor, une fiche ouverte avant la connexion du pont se remplit dès qu'il répond. Les projets existants de `~/projects` défilent avec gauche et droite.
- [x] « Nouveau projet… » avec un nom dicté crée le dossier (dépôt git) et y lance une session Codex, qui travaille dedans.
- [x] La capture de l'écran du haut ne montre plus de chevauchement.
- [x] Les portes de qualité sont vertes (`./gradlew check`, `npm run check`).


## Rapport

- Catalogue réactif à la connexion, état visible, gauche/droite : NewSessionTest,
  captures 02–06 ; jeton app temporairement déplacé puis restauré, sans fermer la fiche.
- Nouveau projet L2 : meteo-du-matin, dépôt vide sur main, puis travail Codex
  preuve.text contenant Bonjour sans commit. new-repository.txt, codex-work.txt,
  codex-events-final.json, captures 08–13. R2 : fiche meteo-du-soir (14–16), annulée.
- Collisions et noms invalides : tests domaine/adapter/WS, captures 17 et 21–22, RPC
  project_exists et bad_project_name ; 39 sessions après comme avant le refus.
- Marges système communes : test réel du conteneur, captures haut/bas 14–19,
  mode un seul écran forcé. ADR 05 ; protocole et ADR 02/13 complétés.
- Portes : Android 306 tests + lint ; pont 283 tests + typage/lint, verts.
- Une session Codex seulement, aucun Claude, aucune permission accordée, aucun reboot.
- Relecture solo du diff main...HEAD après fa2a9d3 : partiel du nom dans le mauvais
  champ et refus masqué corrigés, deux régressions rouges puis vertes, captures 20–22.
  Aucun autre blocage trouvé. Détail relecture.txt. Aucun push ni fusion.
- Quitter a arrêté le Codex, le pont et l’app à 10:36:56 Paris (quit.txt).
  Jeton restauré, vraie source micro et deux écrans remis, forward retiré.
- Vérifications humaines : ouvrir Réglages voix et autoriser le micro si demandé ;
  X → Codex → Projet → Nouveau projet ; L2 dicte un nom inédit, puis la consigne ;
  relire, choisir le niveau, A ; R1/A sur les actions voulues. Essayer aussi R2
  « lance Codex sur un nouveau projet qui s’appelle [nom] pour [demande] ».
  Éteindre/rallumer physiquement l’écran bas pour vérifier le repli, puis Quitter.
- ASR synthétique seulement : « aucun commit » entendu « aucun commis » ; l’agent
  n’a fait aucun commit. Vraie voix et extinction physique restent à vérifier.
- Méthode complète et limites : artifacts/screenshots/15/essai-thor.txt.
