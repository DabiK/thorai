# ADR 18 — Session tout-en-un

Statut : accepté, 14 novembre 2026. Demande produit explicite du ticket 18.

## Validations et options

Le fil contient une carte de validation de **sa propre session**, avec les données
exposées par l’agent : commande, justification, cibles, plan, avant/après et A/X/B.
Les informations absentes sont annoncées, jamais déduites d’une commande.
Les mêmes décisions sont accessibles sur le bas. La file séparée reste disponible
depuis la régie et les notifications ; R1 dans une session parcourt sa file locale,
jamais celle d’un autre projet. Sinon, R1 garde la navigation vers la session voisine.
Cela remplace la navigation du ticket 07 décrite dans l’ADR 08.

`ReviewApprovals.followSession` filtre dans le domaine. La composition crée une
revue dédiée au fil et partage `AnswerApproval` avec la file et la dictée. Après
réponse, la même identité reste affichée, boutons désactivés : un double A/X/B
ne répond pas à la demande suivante. R1 sélectionne explicitement la suivante.
Sans autre demande, Gauche ou « Fermer la validation » retire la carte résolue.
Le panneau inférieur conserve aussi ces actions tant que la carte existe : il ne
réaffiche pas X « Voir le diff » avant sa fermeture. Un nouvel événement peut alors
présenter la prochaine demande. Une décision
programmée pour une ancienne carte vérifie aussi son identité à l’exécution.
Le lien « Régie » navigue explicitement ; il ne doit pas être traduit en B/refus.

Droite ouvre les options dans le panneau du bas : modèle, réflexion, validation,
autres sélecteurs ACP et reprise restent ceux du ticket 17. Les réglages appliqués
et leurs accusés ne changent pas. Le fil et son état restent montés sur le haut ;
le brouillon et les voisines restent sur le bas. En mode un écran, le panneau
occupe 260 dp sous le fil, avec liste défilable et actions épinglées. Start garde
l’interruption, même pendant un réglage occupé. Les décisions tactiles de la carte
visent son identité, indépendamment du focus des options.

## Fil et rendu

- Liste dans l’ordre chronologique, clés stables : le paragraphe consulté garde sa
  position même quand **le même élément** de réponse continue de grandir.
- Suivi de la fin au chargement et pendant les fragments ; remonter au stick,
  à la croix ou au doigt le suspend. Repère tactile « Revenir en bas » ; Gauche
  revient aussi en bas, sauf lorsqu’il ferme la carte résolue. L’observation du
  scroll ignore son émission initiale, qui confondait le chargement avec un geste.
- CommonMark 0.32.0 existant conservé. Coloration lexicale légère, sans grammaire
  propre à chaque langage : commentaires, chaînes, mots-clés courants et nombres.
  Le texte, y compris une chaîne ou clôture Markdown incomplète, n’est jamais modifié.
- Actions sur une ligne, dépliables au toucher : sortie disponible (fin bornée à
  4 Ko, limite existante de la passerelle) et fragments de diff exposés. Aucun
  accès au disque ou nouvel appel d’agent pour afficher ces détails.
- Horodatages HH:mm dans le fuseau de l’appareil, provenant du premier événement
  du pont pour chaque élément ; aucune date inventée pour les éléments sans date.
  Métadonnées purgées avec les 300 éléments du fil. Les envois optimistes n’héritent
  pas de l’heure de l’événement précédent.
- Mode, modèle, réflexion et statut restent sous/dans l’en-tête fixe. Polices Geist,
  surfaces sombres, accents bleu/ambre/vert/rose et panneau latéral conservés.

Les barres restent dans les marges sûres de l’ADR 05. Contraste imposé de navigation
désactivé, icônes de navigation claires sur le fond sombre de Régie. Aucun changement
des réglages Android ni mode immersif forcé.

## Sources et vérification

Maquettes `Session.dc.html` et `Validation.dc.html` lues dans l’export local décrit
par l’ADR 08 ; empreintes dans `artifacts/screenshots/18/maquette-sha256.txt`.
La consultation distante échoue : fraîcheur distante non vérifiée.
Documentation officielle Android consultée avec Context7 (`library`, puis `docs`) :
[LazyListState](https://developer.android.com/develop/ui/compose/lists),
[snapshotFlow](https://developer.android.com/develop/ui/compose/side-effects),
[barres système](https://developer.android.com/develop/ui/compose/system/system-bars).
Extraits `docs-scroll.txt` et `docs-bars.txt`. Aucune nouvelle dépendance.

Tests hors service et hors console : sélection/identité/double réponse, routage
A/X/B, options conservant le fil, sorties/dates/éviction, rendu et dépliage, suivi
initial, stick, croissance du même paragraphe, marges/barres. Essai réel Codex
séparé sur émulateur deux écrans : `artifacts/screenshots/18/essai-emulateur.txt`.
Thor absente ; vérifications physiques explicites dans le ticket.
