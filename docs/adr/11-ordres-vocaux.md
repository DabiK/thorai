# ADR 11 — Ordres vocaux confirmés

Statut : accepté, 14 novembre 2026 (ticket 09).

## Interprétation et fournisseur

R2 utilise le même port de capture et le même fournisseur de transcription que L2 :
local par défaut. `InterpretVoiceCommand` applique d'abord des règles françaises
ancrées sur la phrase entière, avec normalisation de casse, accents et ponctuation.
Les noms de session, projets ou agents doivent désigner une cible unique ; aucune
première correspondance arbitraire. Une négation, condition reconnue ou succession
d'ordres est refusée. Un silence, une capture échouée ou un texte trop long ne part
pas au modèle. L2 reste exclusivement une dictée de message.

Pour les autres tournures : **Mistral, Ministral 3 3B `ministral-3b-2512`**, version
figée, HTTP Chat Completions avec un seul outil `propose_command`, `tool_choice=auto`,
`parallel_tool_calls=false`, température zéro, sortie de 400 tokens au plus. Le
modèle peut s'abstenir. Ce petit modèle francophone évite de démarrer un agent de
code pour interpréter un ordre et ne consomme aucun quota Claude Code. L'alternative
locale ajouterait un second moteur et un modèle sur la console ; elle pourra remplacer
cet adapter derrière `VoiceCommandModel` sans changer le domaine.

La clé Mistral est facultative, saisie par l'utilisateur dans les réglages voix,
chiffrée avec le Keystore existant, jamais loguée ni sauvegardée. Sa saisie active
le repli cloud ; l'écran explique que l'API est payante et reçoit la phrase ainsi
que les noms de sessions/projets (aucun fil, contenu de fichier ou audio). Sans clé,
les règles fonctionnent ; une phrase ambiguë reste explicitement non comprise.
Aucun appel réel à Mistral n'a été effectué lors du développement : clé à saisir et
qualité du modèle à vérifier avec l'utilisateur. Les tests utilisent un faux HTTP.

Le module `:command-model` ne contient que traduction HTTP/JSON, annulation réseau,
délai de dix secondes et décodage borné à 64 Ko. Il n'exécute jamais un outil.
Le domaine impose un délai de douze secondes et valide à nouveau toutes les cibles,
fonctions et champs proposés. Plusieurs appels, fonction inconnue, champs inconnus,
JSON erroné, quota ou réseau indisponible donnent un ordre non compris, sans effet.

Références consultées avec Context7 (`library`, puis `docs`) :
- [définition officielle de Ministral 3 3B](https://github.com/mistralai/platform-docs-public/blob/main/src/schema/models/models/ministral-3-3b-25-12.ts) ;
- [contrat Chat Completions](https://github.com/mistralai/platform-docs-public/blob/main/openapi.yaml) ;
- [appel de fonctions](https://github.com/mistralai/platform-docs-public/blob/main/src/content/en/docs/studio/conversations/function-calling/page.mdx).
Extraits conservés avec ceux d'OkHttp et Compose dans `artifacts/screenshots/09/docs-*.txt`.
Versions Kotlin, Compose, OkHttp et JSON inchangées.

## Confirmation et effets

`ReviewVoiceCommand` fige au début de R2 les sessions, la session affichée/sélectionnée,
la validation effectivement affichée et le catalogue des projets. Aucun partiel,
ni résultat final arrivé avant relâchement, ne permet de confirmer. Après relâchement,
le texte entendu et l'action précise sont affichés. A confirme une seule fois ; B
annule, sans devenir un refus de validation. R2 permet de redire. L1/R1/L2 ne changent
pas de cible pendant la relecture. Les résultats incertains ne sont jamais renvoyés.

`ExecuteVoiceCommand` porte les effets dans le domaine :
- autoriser une fois ou refuser la demande exacte, via le même `AnswerApproval` que
  les validations ordinaires ; demande disparue ou modifiée = aucun envoi ;
- interrompre le tour d'une session, ou les sessions occupées présentes à R2 ; aucune
  session apparue entre-temps n'est ajoutée. `session.cancel` n'est pas une garantie
  d'arrêt des sous-processus (limite Codex déjà observée, ADR 07) ;
- ouvrir une session par son titre, projet ou nom d'agent quand la cible est unique ;
- faire le point écrit d'une session ou de toutes les sessions connues, historique
  compris, selon le même `WrittenSummary` que Y ; lecture du journal uniquement,
  aucun nouveau tour, aucune synthèse vocale. Bilan absent explicitement signalé.
  **Remplacé au ticket 25 (ADR 19)** : « où en est … » ouvre la session visée, « où en
  sont toutes les sessions » ouvre la vue d'ensemble, sans point écrit ;
- lancer une session parmi `projects.list`, avec agent et projet explicites, branche
  et consigne facultatives. Le mode du brouillon est **plan d'abord**, visible avant A.
  `session.create` est envoyé une fois ; identité reçue = ouverture de la session,
  accusé perdu = résultat incertain. Aucune opération git dans Régie.

Le lancement minimal est utilisable dans ce ticket pour vérifier l'effet de l'ordre.
Le modèle `NewSessionDraft` fournit la jonction vers le ticket 11 : fiche complète,
correction à la croix, base de branche et choix du niveau restent à ce ticket. Aucun
champ non dit n'est inventé, notamment ni nom de branche ni consigne. Le catalogue
se recharge à chaque connexion ; il est revérifié juste avant la création.

Choix mineurs : confirmation également pour les lectures/navigation ; autorisation
vocale limitée à une fois ; « tout » fige la liste affichée avant A. Les règles de
sécurité « vraie main » ont été supprimées par l'utilisateur (journal) : les appuis
`sendevent` suivent le même chemin que les appuis physiques.

## Interface et preuves

Exports locaux `Dictee.dc.html` et `Lancer.dc.html` lus intégralement (empreintes dans
les preuves). Aucun outil Artifact connecté ; fraîcheur distante non vérifiée.
Même fond, polices, texte transcrit de 25 sp, confirmation en bas ; panneau compact
en un écran, contenu défilable par croix et stick. Pas de fausses sessions sur Thor.

Les mesures et vérifications finales sont consignées dans `artifacts/screenshots/09/essai-thor.txt`.
Le taux sur vingt phrases lues **par l'utilisateur** reste **non mesuré** : il dort.
Le corpus WAV Thomas est un essai synthétique du moteur local réel, pas cette mesure.

Première mesure synthétique : **14/20, 70 %**, `corpus-results.json`. Les six échecs
proviennent des transcriptions listées dans le rapport ; pas de cible approximative
choisie pour masquer une erreur de nom. Les vingt textes de référence passent les
règles dans le test de domaine ; ce test ne mesure pas la reconnaissance acoustique.

## Relecture solo après le premier commit

Quatre régressions rouges puis vertes (`review-red.txt`) : maintien tactile de
« Redire » (le clic au relâchement ouvrait puis fermait immédiatement la capture),
variantes ASR strictes « Mais/Met en pause » et « Interrompt cette session », refus
conditionnel (« approuve si… »), rejet d'un appel de fonction tronqué. Les réponses
Mistral doivent finir par `tool_calls` et contenir exactement un appel. La règle
« session encore occupée » de Start est exécutée uniquement dans le domaine.
Porte finale : 250 tests Android, aucun échec/ignoré, lint vert.


Mesure finale synthétique, APK corrigé : **15/20, 75 %**, conservée intégralement
sous `final-corpus/`. Les variantes 06/08 sont maintenant reconnues ; les erreurs de
noms 12/16/19/20 restent refusées. L'essai 01 a donné un texte vide à froid, puis le
même WAV a été correctement interprété par le maintien tactile de cinq secondes
(captures 23–24) ; cause non isolée, reprise séparée hors du taux 15/20. Pas de taux
humain inventé, ni de preuve du modèle distant sans clé.

Thor finale : refus, autorisation unique, pause, points individuels/globaux, navigation
et création réelle Codex en plan confirmés ; aucun ordre de contrôle ajouté au fil
comme message. Deux sessions d'essai seulement, arrêtées par Quitter ; pas de Claude.
La pause de `sleep 90` annule le tour (seq 2652), mais la commande termine ensuite :
limite héritée d'ACP/Codex, explicitement annoncée dans le résultat de l'ordre.
