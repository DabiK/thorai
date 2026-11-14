# 18 — Session tout-en-un : validations dans le fil, options sur place, interface plus fluide

Statut : à fusionner
Bloqué par : 17
Possède : `android/`

## Objectif

Retour de l'utilisateur, le 14 novembre :

> « Quand je suis dans la page avec le projet ouvert, le chat qui défile et une demande de permission, j'ai pas envie d'ouvrir une autre page. J'aimerais que tout soit au même endroit, rester dans le chat. »

Il demande aussi de travailler l'interface pour qu'elle soit plus fluide et plus intéressante pour un développeur.

## À livrer

- **Validations dans le fil.** Une demande de l'agent apparaît comme une carte en bas du fil, avec la commande ou le diff, le « pourquoi » et les actions A, X, B. On y répond sans quitter la session. L'écran Validation séparé ne sert plus qu'à la file depuis la vue d'ensemble, ou disparaît.
- **Options sur place.** Sur l'écran du bas de la session, un panneau compact (sans changer de page) montre et change le modèle, le niveau de réflexion et le mode de validation, à partir du ticket 17.
- **Fil plus agréable pour un développeur** :
  - défilement automatique qui s'arrête quand on remonte au stick, avec un repère pour revenir en bas ;
  - Markdown rendu : titres, listes, blocs de code avec coloration ;
  - actions de l'agent repliées en une ligne et dépliables (sortie, diff) ;
  - texte en flux fluide ;
  - horodatage discret ;
  - état de la session et mode visibles en permanence dans l'en-tête.
- **Retouches visuelles notées par l'orchestrateur** : la barre de navigation d'Android est gris clair sous l'écran du haut, alors qu'il faut une barre sombre ou un plein écran immersif.
- **Fidélité au système visuel** de la maquette (`project/Session.dc.html`, `Validation.dc.html`), en améliorant ce qui sert l'usage.

## Critères d'acceptation

- [x] Sur l'émulateur, avec une vraie session Codex : une demande de validation se traite entièrement depuis le fil.
- [x] Le changement de modèle se fait depuis l'écran du bas de la session, sans changer de page.
- [x] Captures avant et après dans `artifacts/screenshots/18/`.

## Rapport

STATUT: done
RÉSUMÉ: la carte du fil masque ses raccourcis lorsque les options capturent la manette.
Les décisions tactiles restent actives ; fermer les options restaure les raccourcis. Ticket à fusionner.
CRITÈRES:
- [x] Validation depuis le fil → vrai Codex, toucher pendant Options, allow_once seq 3210 puis end_turn 3273.
- [x] Modèle depuis le bas → Astra → Terra confirmé, options-model-confirmed-* et options-session.json.
- [x] Captures avant/après → captures initiales conservées et options-* dans artifacts/screenshots/18/.
- [x] Aucun faux A/X/B dans la carte pendant Options → trois régressions intégrées rouges puis vertes, un/deux écrans.
PORTE DE QUALITÉ: android/./gradlew check vert ; 351 tests, zéro échec/ignoré, lint vert (options-android-check.txt).
THOR: absente ; émulateur deux écrans + mode un écran forcé, vrai Codex, touches et toucher injectés. Banc arrêté.
REVIEW: diff main...HEAD relu après f70a59e ; R1/Gauche corrigés aussi dans la carte, trace nettoyée, aucun autre blocage (options-relecture.txt).
DÉCISIONS PRISES: ADR 18 complété ; masquage visuel seulement, callbacks et règles du domaine conservés.
POUR L'UTILISATEUR:
1. Rebrancher THOR0000000001 ; scripts/thor-install.sh ; ouvrir Régie et traiter les permissions demandées.
2. Lancer Codex en mode validation et demander une commande ; Droite ouvre les options : carte sans A/X/B.
3. Préparer Modifs auto puis A : réglage appliqué, commande toujours en attente ; X sans effet ; B ferme et restaure A/X/B.
4. Réouvrir les options et toucher Autoriser une fois ; répéter avec Refuser puis Cette session sur une édition qui le propose.
5. Changer modèle/réflexion depuis le bas, confirmer A ; vérifier le brouillon conservé et Envoyer sans faux raccourci A.
6. Sur une nouvelle demande, maintenir R2, dire « approuve », relâcher puis A ; refaire « refuse », B annule la relecture.
7. Fermer la carte résolue avec Gauche : A Envoyer revient ; tester stick en flux et déplier une action au toucher.
8. Éteindre/rallumer le bas, essayer L2 au vrai micro ; scripts/thor-shot.sh artifacts/screenshots/18/thor puis Quitter.
QUESTIONS: aucune.
CAPTURES: artifacts/screenshots/18/options-{open,closed,single-settled,touch-accepted,model-confirmed}-*.png.
HORS PÉRIMÈTRE: justification ACP parfois absente ; vraie voix/manette/extinction Thor restent à vérifier. Aucun Claude lancé.

Preuves initiales : `artifacts/screenshots/18/essai-emulateur.txt` et `revision-essai.txt` (A/X/B, R2/WAV).
Correction actuelle : `artifacts/screenshots/18/options-essai.txt`, `options-events.json`.
Session Codex, pont et AVD arrêtés (`options-stop.txt`).
