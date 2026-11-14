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
RÉSUMÉ: validations et options réunies dans la session, fil enrichi et barres sombres.
Les deux blocages de revue sont corrigés : raccourci A cohérent et R2 ciblant la carte affichée.
CRITÈRES:
- [x] Validation dans le fil → essais précédents A/X/B ; nouvel essai R2/A, événements 2992 et 3070.
- [x] Modèle depuis le bas → revision-model-confirmed, Astra → Terra confirmé, brouillon conservé.
- [x] Captures avant/après → artifacts/screenshots/18/, before-validation et revision-*.
- [x] Affichage/routage A et contexte R2 → 5 tests intégrés, régressions rouges puis vertes.
PORTE DE QUALITÉ: android/./gradlew check vert, 348 tests + lint ; revision-android-check.txt.
THOR: absente ; émulateur deux écrans, mode un écran forcé, vrai Codex, WAV Thomas/local et touches injectées.
REVIEW: deux blocages corrigés ; diff main...HEAD relu après c35a222, aucun nouveau blocage (revision-relecture.txt).
DÉCISIONS PRISES: ADR 18 complété ; envoi tactile conservé sans puce A quand ce bouton sert ailleurs.
POUR L'UTILISATEUR:
1. Rebrancher THOR0000000001, lancer scripts/thor-install.sh puis Régie ; traiter les permissions demandées.
2. Lancer Codex en mode validation ; tester commande/édition avec les vrais A/X/B depuis le fil.
3. Saisir un brouillon pendant une demande : Envoyer sans A ; A traite la demande et garde le brouillon.
4. Sur une nouvelle demande, maintenir R2, dire « approuve » puis relâcher : rien avant A. Refaire avec « refuse » ; B annule la relecture.
5. Droite ouvre les options sur le bas ; changer modèle/réflexion/mode et confirmer A sans envoyer le brouillon ; B ferme.
6. Fermer la carte résolue avec Gauche : A Envoyer revient. Vérifier le stick pendant le flux et déplier une action au toucher.
7. Éteindre/rallumer le bas, essayer L2 au vrai micro ; scripts/thor-shot.sh artifacts/screenshots/18/thor puis Quitter.
QUESTIONS: aucune.
CAPTURES: artifacts/screenshots/18/revision-{draft-pending,draft-options,voice-review,refuse-current,model-confirmed,single-options,send-restored}-*.png ; captures initiales conservées.
HORS PÉRIMÈTRE: justification ACP Codex parfois absente (signalée) ; vraie voix/manette/extinction Thor non testées. Aucun Claude lancé.

Preuves initiales : `artifacts/screenshots/18/essai-emulateur.txt`.
Correction : `artifacts/screenshots/18/revision-essai.txt`, événements `revision-events-final.json`.
Session Codex, pont et AVD arrêtés (`revision-stop.txt`).
