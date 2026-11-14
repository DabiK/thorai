# 18 — Session tout-en-un : validations dans le fil, options sur place, interface plus fluide

Statut : prêt
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

- Sur l'émulateur, avec une vraie session Codex : une demande de validation se traite entièrement depuis le fil.
- Le changement de modèle se fait depuis l'écran du bas de la session, sans changer de page.
- Captures avant et après dans `artifacts/screenshots/18/`.
