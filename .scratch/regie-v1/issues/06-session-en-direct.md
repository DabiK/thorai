# 06 — Session en direct

Statut : à fusionner
Bloqué par : 05

## Objectif

Écran 3 de la maquette : suivre une session en direct et agir dessus sans clavier.

## À livrer

- **En haut, le fil** : messages de l'utilisateur (avec la mention « dicté »), réponses en flux, actions résumées en une ligne (lu, modifié +/−, lancé, résultat).
- **En haut, à droite** : le plan de l'agent s'il l'expose, et les fichiers modifiés.
- **En bas** :
  - L1/R1 pour passer à la session voisine ;
  - X pour le diff (ouvre l'écran 5 quand il existe) ;
  - Y pour faire le point à voix haute (ticket 12 ; en attendant, un résumé écrit) ;
  - Start pour interrompre ;
  - B pour revenir.
- Envoyer un message écrit de test : le texte libre est un secours, la dictée arrive au ticket 08.

## Critères d'acceptation

- [x] Sur la Thor, on suit une vraie session Claude et une vraie session Codex jusqu'à la fin, on en interrompt une, et on passe de l'une à l'autre avec L1/R1. Preuves : `artifacts/screenshots/06/essai-thor.txt`, captures 10–11 (Claude, passage précédent confirmé par le journal), 16–21 (Codex et navigation, reprise Codex). Start annule le tour ; la limite de la commande shell est explicitée dans le rapport.


## Rapport

- Écran 3 livré : fil en flux, markdown, lectures regroupées, actions et résultats, plan ACP quand présent, fichiers avec +/− par fichier. L'origine « dicté » est testée dans le domaine ; capture vocale au ticket 08.
- L1/R1 suivent toutes les sessions, sans boucler ; R1 reste prioritaire aux validations sur la vue d'ensemble conformément au PRD. X annonce le diff à venir (ticket 10), Y affiche le point écrit (ticket 12), Start annule le tour, B revient. Envoi de secours uniquement par A ou Envoyer.
- Reprise de l'implémentation non commitée, sans sous-agent. Trois défauts corrigés après tests rouges : comptes multi-fichiers attribués au premier fichier, perte des messages annulés avant livraison, lectures échouées regroupées comme réussies.
- `cd android && ./gradlew check` : **174 tests verts**, zéro ignoré, lint vert. `scripts/thor-install.sh` : Success, seul app.regie installé. Version finale essayée en mode un seul écran forcé, puis retour au double écran.
- Sur la Thor : Codex jusqu'à `end_turn`, texte écrit et A, R1 vers l'historique Claude, L1 vers Codex, Start jusqu'à `cancelled`, Y/X/B. Aucune nouvelle session Claude lancée : les captures précédentes ont été conservées et recoupées avec le journal réel.
- Hors périmètre : après `cancelled`, le processus shell `sleep 90` de Codex a continué puis annoncé `completed`. L'app relaie cette vérité ; ne pas confondre annulation du tour et destruction du processus.
- Dépendance utilisateur du ticket 05 : RUN_COMMAND et WRITE_SECURE_SETTINGS non accordées. Pont lancé par le script d'essai existant ; aucun droit accordé ni redémarrage. Marche à suivre détaillée dans `artifacts/screenshots/06/essai-thor.txt`.
- ADR 07 : modèle du fil, bibliothèques, navigation, limites du protocole, clavier. API Compose vérifiée via Context7. Les tests restent sans vrai agent ni console.
- Preuves : `artifacts/screenshots/06/` (captures), `android-check.txt`, `bridge-events.jsonl`, `manette-envoi.txt`, `essai-thor.txt`.
- Sessions d'essai arrêtées par Quitter ; aucune fusion, aucun push.
- Relecture finale de `git diff main...HEAD` : périmètre, frontières domaine/adapters, assertions et provenance des captures vérifiés ; ligne vide finale supprimée et commentaire sur les résultats tardifs corrigé. Aucun critère de démarrage automatique ni de destruction du shell déclaré tenu.
