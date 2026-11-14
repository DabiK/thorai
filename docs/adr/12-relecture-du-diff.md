# ADR 12 — Relecture des éditions exposées par l’agent

Statut : accepté, 14 novembre 2026 (ticket 10).

## Source et limites

La relecture utilise le journal ACP déjà chargé par `AgentGateway.thread`, sans
nouvel accès au disque ni RPC. Elle montre les éditions terminées, regroupées par
fichier, dans leur ordre d’arrivée. Les éditions successives restent des blocs
séparés : les fusionner sans base commune inventerait un état Git. Les comptes
+/− cumulent les lignes des blocs exposés, ce ne sont pas les statistiques d’un
`git diff` net. Une commande shell ne fournit pas nécessairement un diff ; les
fichiers connus sans contenu affichent « Diff non communiqué » et aucun commentaire
n’est possible. La limite existante de 300 éléments du fil s’applique ; ce n’est pas
une archive exhaustive des changements anciens d’une longue session.

`ThreadBuilder` conserve les diff de chaque appel, même si sa confirmation ne
répète pas le contenu. `DiffDecoder` traduit ACP ; `LineDiff` réutilise Myers via
java-diff-utils 4.18, sans nouvelle dépendance. Le domaine reçoit `DiffFragment`,
`DiffLine` et les coordonnées anciennes/nouvelles, jamais un objet de bibliothèque.
Les suppressions portent seulement leur numéro ancien, les ajouts leur numéro nouveau.

**Un extrait n’est pas un fichier entier.** Claude publie des `structuredPatch`
avec `oldStart` / `newStart` : ils donnent les coordonnées exactes (fixture réelle,
ligne ajoutée 5 dans todo.md). Une création (`oldText=null` ou `_meta.kind=add`)
commence à 1. En revanche, les anciens/nouveaux textes d’une mise à jour Codex
peuvent être des extraits sans position : affichage « de l’extrait », numéros
relatifs, et avertissement inclus dans la demande. Aucun numéro absolu deviné.

## Domaine et confirmation

`ReviewDiff` agrège les éditions terminées, conserve la sélection, borne la
navigation, prépare le commentaire et la demande d’explication. Les éditions
échouées ou en attente ne sont pas présentées comme appliquées. Les dépendances
restent `AgentGateway` et des flux Kotlin ; tests avec `StubGateway` uniquement.

L2 fige session, chemin, côté ancien/nouveau, numéro et texte de la ligne.
`ReviewDictation` et `DeliverDictation` du ticket 08 gardent A/B/Y/X et le traitement
des livraisons incertaines. Le message envoyé contient ce contexte et le commentaire.
Les coordonnées sont celles de l’édition affichée : le texte sélectionné permet de
la reconnaître même si l’agent a déjà modifié le fichier depuis.

Y prépare « explique-moi ce bloc » sur l’édition sélectionnée, bornée à 61 lignes
au voisinage de la sélection. A confirme (principe du PRD : rien sans A), B annule.
La demande précise « sans modifier les fichiers », réponse française écrite.
Elle utilise la session et le tour normal de l’agent, sans synthèse ni second modèle.
Le panneau suit les textes après le message utilisateur correspondant, jamais une
ancienne réponse ou celle d’une demande suivante. Double A protégé ; seul un échec
certain permet un nouvel essai. Un accusé incertain n’autorise pas de renvoi automatique.

## Interface

Export local `project/Diff.dc.html` lu en entier, empreinte dans
`artifacts/screenshots/10/maquette-sha256.txt`. Pas d’outil Artifact Claude disponible ;
la fraîcheur distante de cet export n’a pas été vérifiée.

- Haut : chemin, comptes, ancien/nouveau, ajouts verts et suppressions roses,
  sélection blanche, Geist Mono ; retour à la ligne pour garder le code long lisible.
- Bas : fichiers tactiles, comptes, ligne choisie, micro maintenu, Y et B.
- ↑↓ sélectionne une ligne ; ←→ change de fichier ; stick droit fait défiler.
- L1/R1 et Start gardent le comportement de session, avec priorité aux validations.
- Un écran : fichiers en rangée et actions sous le contenu ; deux écrans : liste en bas.
- Adaptation de la maquette : la dictée relue utilise l’écran commun livré au ticket 08,
  avec chemin et ligne, au lieu de créer une deuxième relecture dans une bulle inline.

API Compose vérifiée avec Context7 (`library Jetpack Compose`, puis `docs`) :
[LazyListState et défilement](https://developer.android.com/develop/ui/compose/lists).
Recherche Context7 java-diff-utils sans correspondance ; référence primaire lue :
[dépôt et API du projet](https://github.com/java-diff-utils/java-diff-utils).
Version conservée, signatures vérifiées par compilation et tests des coordonnées.

## Vérification

261 tests Android et lint verts ; rouge puis vert sur les lignes, le décodage réel,
la sélection, l’explication, l’envoi de commentaire, X et le panneau compact.
Thor : Codex réel dans un projet d’essai dédié, WAV Thomas sur le moteur local,
commentaire reçu une seule fois, seuil réellement passé de 70 à 80, explication
retournée, croix/stick/tactile/un-deux écrans. Pas de Claude, permission, reboot,
installation autre que `app.regie`. Preuves et pas à pas :
`artifacts/screenshots/10/essai-thor.txt`. Le microphone humain reste à essayer.

## Relecture solo après commit

Relecture de `git diff main...bec880e` (base `1ec1415`), puis des corrections :

- La croix au bord du fichier doit ramener la sélection hors champ, même si le numéro
  ne change pas. `selectionRevision` signale ce geste au défilement Compose ; deux
  régressions domaine/UI rouges puis vertes. Le stick reste un défilement libre.
- Le relâchement du stick reste traité lorsqu’un panneau voix intercepte les entrées.
  Il ne laisse plus de vitesse périmée au retour dans le diff. Régression de la racine
  de composition sous Robolectric, sans démarrer passerelle, voix ni service.
- La réponse d’explication réutilise le rendu Markdown du fil, y compris son curseur
  pendant la réponse. Régression Compose rouge puis verte sur la réponse terminée.
- Le test de demande d’explication vérifie maintenant le message entier contre un
  attendu littéral (instruction, chemin, ligne et bloc), pas seulement des sous-chaînes.

Porte finale : **265 tests Android, zéro échec ni ignoré, lint vert**. APK réinstallé,
Thor : sélection ramenée en vue (15–16), Codex repris pour une seule nouvelle demande
après deux A (seq 3006), Markdown réel en un/deux écrans (23–24), réglages voix ouverts
pendant un mouvement puis relâchement et retour stables (25–27). Quitter a de nouveau
arrêté l’unique session d’essai et le pont à 04:44:27 locale. Aucun autre agent lancé.
