# 07 — Validations

Statut : bloqué
Bloqué par : 06

## Objectif

Écran 4 de la maquette : répondre aux demandes de validation des agents.

## À livrer

- **En haut** : ce que l'agent veut faire (commande ou fichier), pourquoi s'il le dit, ce que ça touche, et la file des autres demandes.
- **En bas** :
  - A : autoriser une fois ;
  - X : toujours dans cette session, si l'agent le permet ;
  - B : refuser.
- **Refus avec consigne dictée** : branché au ticket 08. Ici, préparer le cas d'usage.
- **Niveau de validation au lancement d'une session** (plan d'abord, modifications automatiques, tout valider), traduit vers les modes de chaque agent.
- **Notification Android** quand une validation arrive alors que l'app n'est pas au premier plan.

## Critères d'acceptation

- [ ] Sur la Thor, une commande et une modification de fichier sont validées ou refusées pour chacun des deux agents, et l'agent reprend en conséquence. Non essayé : arrêt sur erreur d’environnement avant implémentation.


## Rapport

STATUT: failed

Reprise solo Codex du 14 novembre 2026 sur `ticket/07-validations`. Branche initialement propre, aucun commit dans `main..HEAD`. Aucun code changé, aucun agent lancé, aucune installation sur la Thor.

La porte initiale `cd android && ./gradlew check` échoue avant les tests (code 1). Sortie complète : `artifacts/screenshots/07/android-check.txt`.

```text
Could not determine the dependencies of task ':app:lintReportDebug'.
> SDK location not found. Define a valid SDK location with an ANDROID_HOME environment variable or by setting the sdk.dir path in your project's local properties file at '/home/dev/Documents/regie/.worktrees/07-validations/android/local.properties'.
```

Arrêt conformément à la consigne du ticket : « Une commande qui échoue pour une raison d'environnement […] : n'insiste pas, STATUT: failed avec la sortie exacte. » Aucun critère déclaré tenu. Le statut du fichier est `bloqué` puisque la porte de qualité n'est pas verte ; ce n'est pas une décision produit en attente.

### Reprise technique

1. Configurer le SDK dans `android/local.properties` (fichier ignoré), avec le chemin indiqué par `CLAUDE.md` : `sdk.dir=/opt/homebrew/share/android-commandlinetools`. Vérifier que ce SDK existe.
2. Relancer la porte initiale puis reprendre l'implémentation tests d'abord. Le modèle `Approval` ne porte actuellement que l'identifiant, la session, le genre et le sujet ; `OpenApproval` est vide. `AgentGateway` ne permet pas de répondre aux validations.
3. Lire la maquette `project/Validation.dc.html` et `project/Lancer.dc.html` : aucun fichier local trouvé, outil Artifact non disponible dans cette session ; référence visuelle non consultée.
4. Étendre les niveaux du pont : seuls `ask` et `allow-all` existent dans `bridge/src/domain/agents.ts`. Conserver la compensation qui exclut `accept_execpolicy_amendment` (ADR 02) ; ne pas confondre autorisation persistante et autorisation de session.
5. Préparer le refus avec consigne pour le ticket 08, les notifications et les essais Thor. Utiliser Codex pour toute nouvelle session réelle ; aucun nouveau Claude pendant cette reprise.

Documentation Android notifications consultée via Context7 (`library` puis `docs`) : https://developer.android.com/develop/ui/compose/notifications/create-notification et https://developer.android.com/develop/ui/compose/notifications/notification-permission. Pas d'implémentation engagée.

### Vérification avec l'utilisateur après implémentation

1. Accorder les permissions demandées par Régie, notamment les notifications.
2. Dans une session de chaque agent, demander une commande puis une modification de fichier ; ouvrir la validation par R1, accepter par A puis vérifier la reprise dans le fil.
3. Répéter avec B pour vérifier le refus ; essayer X uniquement lorsque l'agent propose une autorisation limitée à la session.
4. Passer Régie en arrière-plan, provoquer une validation, vérifier la notification et son ouverture, ainsi que le maintien de l'écran du bas.

Relecture solo : aucun critère coché sans preuve, aucune modification de production, aucun ADR créé. La suite reste entièrement à implémenter et à vérifier.
