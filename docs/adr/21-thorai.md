# ADR 21 — L'app s'appelle ThorAI

Statut : accepté, 18 novembre 2026 (ticket 30, décision de l'utilisateur).

## Contexte

« Régie » était un nom de travail. L'utilisateur nomme l'app **ThorAI** ; elle n'est pas
destinée à la vente. L'APK de release du ticket 27 demandait déjà une réinstallation : on en
profite pour changer d'identifiant une seule fois.

## Décision

- **Nom visible : ThorAI.** Lanceur, titres et fils d'Ariane, carte volante, service
  d'accessibilité (« ThorAI, mode console »), messages du pont et de l'installeur, release
  (`thorai-<version>.apk`, titre « ThorAI <version> »), README et documentation courante.
- **applicationId `app.thorai`.** ThorAI s'installe à côté de `app.regie` au lieu de la
  remplacer ; l'ancienne app se désinstalle une fois ThorAI reliée au pont. Le pont garde un
  jeton par app (ADR 22) : les deux apps peuvent rester connectées pendant la transition.
- **Noms techniques inchangés** : `namespace` et paquets Kotlin (`app.regie.*`, donc la classe
  de lancement `app.regie.TopActivity`), modules et groupe Gradle de la voix, scripts
  `regie-bridge-*`, dossiers `~/.config/regie` et `/home/dev/.local/share/regie`, dépôt
  `regie`, marqueur de compte rendu `[Point Régie]` (lecture des anciens fils). Les renommer
  ne changerait rien pour l'utilisateur et casserait les installations existantes.
- **Lancer l'app** depuis un script : par son lanceur, résolu
  (`cmd package resolve-activity --brief -c android.intent.category.LAUNCHER <paquet>`), puis
  `am start -n`. `am start -p` exige `CATEGORY_DEFAULT`, qu'un lanceur n'a pas.
- **Consignes des agents** (`thor/computer-use/consignes.md`) : elles nomment ThorAI et
  `app.thorai`. Elles arrivent sur la console au prochain `thor/computer-use/install.sh` (ou
  `thor/install-on-device.sh`) ; une session existante garde les anciennes.
- Les ADR, tickets et captures antérieurs gardent « Régie » et `app.regie` : ce sont des
  traces. Seules les commandes encore en usage (ADR 05, 06, 14) passent à `app.thorai`.

## Conséquences

- Nouvelle app, nouvelles données : préférences, modèle de voix et permissions sont à
  redonner (parcours d'accueil, ADR 23). Projets, sessions et comptes, dans Termux, restent.
- Deux services « mode console » lisent la même manette : n'activer que celui de ThorAI.
