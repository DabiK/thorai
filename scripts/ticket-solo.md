# Ticket en solo (Codex)

Tu mènes un ticket du dépôt regie de bout en bout, sans intervention humaine et sans sous-agents. Tu es déjà dans le worktree, sur la branche du ticket. Le ticket est à la fin de ce message.

## Étapes

1. **Explorer.**
   - Lis `CLAUDE.md` en entier, ainsi que `docs/adr/00-journal-de-reprise.md` (décisions de l'utilisateur, ordre des tickets).
   - Lis la section de `docs/PRD.md` que le ticket concerne et les ADR de `docs/adr/` qui touchent ton périmètre.
   - Pour un écran, lis la maquette (fichiers `.dc.html` du canvas, voir `CLAUDE.md`).
   - Regarde ce qui existe déjà avant d'écrire. Si `git status` montre des modifications non commitées, c'est le travail d'un passage précédent : reprends-le s'il est sain.
2. **Implémenter.**
   - Tests d'abord.
   - La logique va dans le domaine, derrière des ports ; les adapters ne contiennent pas de règle métier.
   - Les tests n'appellent jamais un vrai service, ni un vrai agent, ni la console.
   - Pour une API de bibliothèque, vérifie la documentation à jour.
3. **Vérifier.**
   - Lance la porte de qualité de chaque partie touchée : `./gradlew check` dans `android/` ou `voice/`, `npm run check` dans `bridge/`. Corrige jusqu'au vert, sans affaiblir ni désactiver aucun test.
   - Teste ensuite sur la Thor (adb, série `THOR0000000001`) : `scripts/thor-install.sh`, puis `scripts/thor-shot.sh artifacts/screenshots/<NN>`.
   - Si un essai demande une vraie session d'agent sur la console, **lance-la avec Codex, pas avec Claude** : le quota Claude de l'utilisateur est presque épuisé.
4. **Commiter.**
   - Conventional Commits (`type(scope): sujet`), fichiers ajoutés par leur chemin, pas de `--no-verify`.
   - Coche dans le fichier du ticket les critères tenus, remplis sa section « Rapport », passe son statut à `à fusionner` (ou `bloqué`), ajoute une ligne au journal de reprise, et commite le tout.
5. **Te relire.**
   - Relis `git diff main...HEAD` comme un relecteur qui ne connaît pas ton raisonnement.
   - Cherche : un critère déclaré tenu sans preuve, un test qui passerait encore si la fonctionnalité était cassée, une règle métier hors du domaine, du travail hors périmètre, un écart avec la maquette.
   - Corrige, relance l'étape 3, commite.
6. **Rendre compte.** Termine par le rapport ci-dessous, et rien après.

## Quand il faut l'utilisateur

Personne ne répond avant le matin. Ne bloque pas le ticket pour une étape qui demande l'utilisateur : geste de sécurité sur la console (permission, association, boîte de dialogue), saisie d'une clé, lecture au micro, redémarrage.
- Fais tout le reste.
- Simule ce qui peut l'être : appuis de manette par `sendevent`, voix par des WAV générés sur le Mac avec `say -v Thomas`.
- Écris pas à pas la vérification à faire avec l'utilisateur.

Si tout ce qui ne dépend pas de lui est vert, le statut est `à fusionner`.

## Limites

- Si `git status` annonce une fusion en cours avec des conflits : résous-les en gardant les deux intentions, puis `git add` et `git commit`.
- Reste sur la branche du ticket. Ne pousse pas, ne fusionne pas dans `main`, ne touche pas aux worktrees, n'utilise pas `gh`.
- Sur la console :
  - n'installe que `app.regie` ;
  - ne redémarre pas la console ;
  - arrête les sessions d'agents que tu as lancées.
- Reste dans le périmètre du ticket. Un problème hors périmètre se signale dans le rapport.
- Un choix mineur et réversible se tranche et se note. Une décision durable se consigne dans un ADR.
- Une décision produit absente du PRD et difficile à défaire : `STATUT: blocked` avec la question.
- **Une erreur d'environnement que tu peux lever toi-même, lève-la et continue** : dépendance manquante (`npm ci` dans `bridge/`, sans `--omit`), `local.properties`, build à relancer, pont à redémarrer, appli à réinstaller. `STATUT: failed` seulement pour un obstacle hors de ta portée (console débranchée, réseau coupé, quota), avec la sortie exacte.

## Rapport

Quarante lignes au plus, dans ce format exact :

```
STATUT: done | blocked | failed
RÉSUMÉ: deux ou trois lignes sur ce qui fonctionne maintenant.
CRITÈRES:
- [x] critère → preuve (test, commande ou capture)
- [ ] critère non tenu → pourquoi
PORTE DE QUALITÉ: résultat de ./gradlew check ou npm run check
THOR: ce qui a été testé sur la console, et comment
REVIEW: ce que ta relecture a trouvé et corrigé
DÉCISIONS PRISES: choix tranchés sans l'utilisateur, ADR créés
POUR L'UTILISATEUR: vérifications à faire avec lui, pas à pas
QUESTIONS: ❓ éventuels, compréhensibles sans contexte
CAPTURES: chemins des captures à regarder
HORS PÉRIMÈTRE: problèmes vus et non traités
```
