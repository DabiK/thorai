# 15 — Nouveau projet depuis Régie, liste des projets à jour, barre d'état

Statut : prêt
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

- Sur la Thor, une fiche ouverte avant la connexion du pont se remplit dès qu'il répond. Les projets existants de `~/projects` défilent avec gauche et droite.
- « Nouveau projet… » avec un nom dicté crée le dossier (dépôt git) et y lance une session Codex, qui travaille dedans.
- La capture de l'écran du haut ne montre plus de chevauchement.
- Les portes de qualité sont vertes (`./gradlew check`, `npm run check`).
