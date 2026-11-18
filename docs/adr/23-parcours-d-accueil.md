# ADR 23 — Parcours d'accueil au premier lancement

Statut : accepté, 18 novembre 2026 (ticket 28).

## Contexte

Une personne qui installe l'APK doit arriver, sans lire la notice, jusqu'à sa première session.
Avant ce ticket, l'app demandait RUN_COMMAND et les notifications à l'ouverture, puis
laissait des avis permanents (mode console, accès des agents à Android) sur l'accueil.

## Décision

- **Neuf étapes**, dans `SetupStep` (domaine) : bienvenue ; Termux et Debian ; pont ; voix ;
  notifications ; mode console, accès à Android et comptes des agents, facultatifs ; fin.
  En haut, l'explication et le chemin parcouru ; en bas, l'état vérifié et les actions, au
  doigt comme à la manette (A, B, X, Y). Un seul écran : les deux, côte à côte.
- **L'état réel, relu chaque seconde** tant que le parcours est ouvert (`SetupChecks`) :
  Termux installé, permissions accordées, pont joint (`Overview.bridge`), modèle de
  transcription installé ou en cours, service d'accessibilité activé, `WRITE_SECURE_SETTINGS`,
  débogage sans fil, comptes des agents (`auth.status`, toutes les trois secondes). Les étapes
  passent seules au vert ; A continue.
- **Ce qui ne s'observe pas** : la Debian se prouve par le pont joint (étape suivante) ;
  l'association du débogage sans fil, par `WRITE_SECURE_SETTINGS`, accordée par la même
  commande juste après l'association.
- **Rien n'est accordé à la place de l'utilisateur.** RUN_COMMAND, micro et notifications :
  demande Android, depuis l'activité de l'écran tactile ; refusée pour de bon, A ouvre les
  infos de l'appli. Accessibilité et développeur : l'app ouvre l'écran des réglages.
  Accessibilité s'ouvre en bas (tactile), les options pour les développeurs en haut, à la
  croix, pour que la fenêtre d'association reste visible pendant qu'on tape dans Termux, en bas.
- **Commandes copiées** : Y copie les commandes de l'étape et ouvre Termux sur l'écran du bas.
  Celles de l'association demandent le port et le code (`read`), pour qu'aucune ligne collée
  ne parte avec un emplacement à remplir. Jamais de secret dans le presse-papiers.
- **Voix** : seul le modèle de transcription (~488 Mo, 1,2 Go libres) se télécharge, avec sa
  progression ; la synthèse, inutilisée par l'app, reste au laboratoire (`prepareSpeechToText`).
  Le téléchargement continue si l'on avance.
- **Passer** : les étapes facultatives (X). Une étape requise ne se passe que si Android l'a
  refusée pour de bon (micro, notifications) ou si le téléchargement échoue ; jamais le pont.
  « Plus tard », au doigt, referme le parcours.
- **Une seule ouverture automatique** : au premier lancement, sur la bienvenue. Mené au bout ou
  refermé, il ne revient plus seul (`SetupMemory`, préférences privées). **Configurer**, sur
  l'accueil, le rouvre à la première étape non faite. Un point ambre à côté signale une étape
  facultative restante, à la place des avis permanents, retirés (ticket 30).
- Pendant le parcours, les demandes du premier lancement et l'offre de connexion des agents
  (ADR 18) se taisent : l'étape des comptes ouvre l'écran du ticket 23, puis rend la main.
- Le choix de Termux ou du pont externe se fait à chaque demande (`ConsoleBridge`) : installé
  pendant le parcours, Termux sert sans rouvrir l'app.
- En debug seulement, `--es setup_step <ÉTAPE>` ouvre une étape, pour la capturer.

## Conséquences

- Les captures de chaque étape se prennent sur la console sans accorder de permission.
- Une installation où tout est déjà en place traverse le parcours à coups de A.
- Le parcours n'installe rien dans Termux : l'installeur reste `thor/install-on-device.sh`.
