# Fixtures Scribe realtime

Messages du serveur `wss://api.elevenlabs.io/v1/speech-to-text/realtime`, rejoués par
`ScribeServer` dans les tests. Ils suivent le schéma de la référence d'API et des types du SDK JS
officiel (`elevenlabs-js`, `src/wrapper/realtime/connection.ts`), relus le 13 novembre 2026.

**Ils ne sont pas encore issus d'une vraie session** : aucune clé n'est disponible pour l'agent.
À remplacer par une capture réelle quand l'appli d'essai tournera avec la clé de l'utilisateur.

- `onOpen` : envoyés à l'ouverture de la connexion ;
- `onAudio` : le n-ième est envoyé après le n-ième morceau d'audio reçu ;
- `onCommit` : envoyés quand le client envoie `commit: true`.
