# ADR 01 — Architecture d'ensemble et briques réutilisées

Statut : accepté, 13 novembre 2026 (cadrage avec l'utilisateur, hors ticket).

## Contexte

L'utilisateur veut une app Android centrée sur la voix pour piloter Claude Code et Codex, qui tournent déjà sur la Thor. La valeur est dans l'expérience de l'app, pas dans le pilotage des agents. Consignes :
- réutiliser au maximum ce qui existe ;
- isoler la voix derrière des ports, pour brancher plus tard une transcription gratuite à la place d'ElevenLabs.

## Décision

```
┌──────────────────────── AYN Thor ────────────────────────┐
│  App Régie (Kotlin, Compose)          Debian (proot)     │
│  ┌─────────────┐ ┌─────────────┐     ┌───────────────┐   │
│  │ écran haut  │ │ écran bas   │     │ pont (Node)   │   │
│  │ activité 0  │ │ activité 4  │     │ WebSocket     │   │
│  └──────┬──────┘ └──────┬──────┘     │ 127.0.0.1 +   │   │
│         └─ domaine ─────┘  ◀──WS──▶  │ jeton         │   │
│            ports : AgentGateway       ├───────┬───────┤   │
│                    voix (voice/api)   │Claude │ Codex │   │
│                                       └───────┴───────┘   │
└───────────────────────────────────────────────────────────┘
```

1. **App Android native** en Kotlin et Jetpack Compose. Un seul processus, deux activités : écran haut (`displayId` 0) et écran bas (`displayId` 4). Elles partagent le même état. Flutter, React Native et une WebView gèrent mal le second écran, la manette et le micro.
2. **Pont dans la Debian**, en Node/TypeScript. Il démarre et garde les sessions des deux agents, et les expose à l'app par un WebSocket local (`127.0.0.1`, jeton obligatoire). Il garde les événements pour que l'app puisse se reconnecter et rattraper ce qu'elle a manqué.

   **Rien ne démarre au boot** (décision de l'utilisateur, 13 novembre). C'est l'app qui, à son lancement, démarre le pont par l'intent RUN_COMMAND de Termux. Il faut pour cela `allow-external-apps=true` dans Termux et la permission accordée à l'app. RUN_COMMAND ne sert qu'à démarrer et arrêter le pont : il coupe les sorties vers 100 Ko, donc le suivi passe par le WebSocket.
3. **Pilotage des agents : ACP retenu par l’ADR 02.** Les deux candidats du cadrage, conservés ici comme historique :
   - **ACP (Agent Client Protocol)**, avec les adaptateurs maintenus `@zed-industries/claude-agent-acp` et `codex-acp`. Ils exposent les deux agents dans un même schéma, qui couvre déjà toute la maquette : messages en flux, appels d'outils avec diff, plan de l'agent, demandes de validation, modes, reprise de session. C'est le candidat préféré : le pont se réduit alors à multiplexer des sessions ACP sur le WebSocket.
   - **Des adapters internes existants**, en repli lors du cadrage, non retenus. Ils étaient en architecture hexagonale. Il pilote Claude par l'Agent SDK et Codex par `codex app-server`, avec un port commun à huit événements et des tests de contrat. Il est testé sur Claude Code 2.1.292 et Codex 0.160.1 (ADR 03 de ce dépôt-là).
4. **La voix derrière des ports** (`voice/api`, Kotlin pur) : transcription en flux, synthèse, détection de parole, vocabulaire à favoriser.
   - Adapters : sherpa-onnx local par défaut depuis la décision du 13 novembre, ElevenLabs en option, et une voix factice. Depuis le 14 novembre, Régie ne fait aucune synthèse vocale (ADR 09/10) ; le laboratoire conserve ce port.
   - Le choix se fait par un réglage.
   - La voix vit dans un build Gradle séparé (`voice/`), avec une appli d'essai. L'app l'inclut en composite build, ce qui permet de la développer en parallèle de l'interface.
5. **Accès des agents à Android (computer use)** par des outils existants : un serveur MCP de pilotage d'Android et adb en local sur la console. Il est déclaré aux deux agents, comme le navigateur `thor_browser` l'est déjà.
6. **Développement en parallèle** : la passerelle factice du domaine rejoue des événements enregistrés sur le vrai pont (`bridge/fixtures/`). L'interface peut donc avancer avant que le pont soit branché.

## Conséquences

- Le contrat entre l'app et le pont est écrit par le ticket 02 dans `docs/protocole-pont.md`, avec des enregistrements réels. Les tickets d'interface s'appuient sur la passerelle factice jusqu'au ticket 05.
- Une mise à jour de Claude Code, Codex ou des adaptateurs peut casser le pont. Les versions testées sont déclarées, et l'app avertit quand on en sort.
- Rien n'est installé sur la console sans être scripté dans `thor/`, pour pouvoir le refaire après une réinitialisation.
