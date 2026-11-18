# 25 — Mode console : parler à l'agent depuis n'importe quelle appli et le suivre

Statut : prêt
Possède : `android/`

## Retour de l'utilisateur, 18 novembre

> « J'aimerais que l'application puisse passer en arrière-plan et que je puisse quand même parler à l'agent : lui demander d'effectuer des actions sur la console sans perdre le contexte, sans revenir à l'appli pour chatter. Qu'il contrôle vraiment la console, qu'on puisse le suivre et le guider au fur et à mesure, un peu comme s'il incarnait la console elle-même. »

## À livrer

- **L2 maintenu fonctionne partout**, même quand une autre appli a le focus sur l'un ou l'autre écran.
  - Piste : service d'accessibilité de Régie qui filtre les touches de la manette (`canRequestFilterKeyEvents`). Il ne consomme que L2/R2, laisse passer tout le reste, et s'active une fois par l'utilisateur.
  - Le message dicté part vers la session « au premier plan » de Régie.
- **Suivre l'agent sans revenir dans l'appli.**
  - L'écran du bas garde Régie : la session en cours en version compacte (derniers messages, actions, validation en attente), la dictée, les réglages. L'écran du haut, lui, montre ce que l'agent pilote.
  - Si l'écran du bas est éteint ou occupé : une bulle flottante discrète sur l'écran du haut (`SYSTEM_ALERT_WINDOW`), avec l'état de l'agent et sa dernière phrase.
- **Message volant** (retour du 18 novembre) : quand l'agent envoie un message et que Régie n'est pas visible à l'endroit où l'utilisateur regarde, le message s'affiche dans une carte flottante sur l'écran du haut, par-dessus l'appli en cours. On voit l'agent, sa phrase, et éventuellement sa question. La carte s'efface seule après quelques secondes, ou avec B. Avec L2, on répond directement depuis la carte, sans revenir dans Régie. Pas de carte pour les fragments en cours de flux : seulement pour un message terminé, une question ou une validation demandée.
- **Une session « console »** : un raccourci pour lancer ou reprendre une session dont le dossier de travail est neutre, prévue pour piloter la console plutôt qu'un projet.
- **Les permissions spéciales** (accessibilité, superposition) sont demandées avec une explication courte. Ce sont des gestes de l'utilisateur.

## Critères d'acceptation

- Sur la Thor, Chrome ouvert en haut : L2 maintenu, « ouvre YouTube et cherche lofi », relâché, A → l'agent le fait sous les yeux de l'utilisateur, qui suit le fil sur l'écran du bas.
