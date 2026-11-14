# Textes de Régie

On tutoie. Chaque phrase d’interface tient en huit mots au plus.
On compte les mots, pas les signes de ponctuation. Une erreur peut
avoir deux phrases : le constat, puis le geste utile.

Les boutons commencent par un verbe à l’infinitif : « Envoyer »,
« Autoriser une fois », « Refuser », « Revenir ». Une sélection porte
le nom de sa valeur ; un titre nomme son contenu.

Pas de jargon lorsqu’un mot simple suffit : « modifications » remplace
« diff », « non communiqué » remplace « non exposé ».
Pas d’emoji, d’enthousiasme artificiel ni de personnification de l’app. Pas de rappel permanent de la confirmation
quand le bouton la montre déjà. Une aide reste si elle révèle un geste
invisible, comme maintenir L2 ou faire défiler au stick.

| Concept | Libellé |
| --- | --- |
| Travail d’un agent | Session |
| Accord demandé par un agent | Validation |
| Instruction donnée à l’agent | Consigne |
| Dépôt choisi | Projet |
| Politique de validation | Mode |
| Choix natifs | Modèle, Réflexion |
| Éditions exposées par l’agent | Modifications |
| Bilan écrit | Point de session / Faire le point |

Les noms de modèles et les autres options natives viennent de l’agent.
Les consignes, réponses, chemins, commandes, extraits et erreurs externes
sont des **données**, pas des textes à réécrire. On conserve leur contenu,
y compris leurs mots, ponctuation et longueur. Les messages de Régie qui
les entourent suivent ce guide. Les commandes de dépannage restent dans
le README. Une limite de données reste annoncée : écriture par commande
absente du suivi, résultat inconnu, information non communiquée.

On utilise l’apostrophe typographique et les espaces insécables U+00A0
avant « : ? ! » et à l’intérieur des guillemets français. On ne les
injecte jamais dans les identifiants de protocole ou dans le code.

## Deux écrans, mêmes repères

Conserver Geist/Geist Mono, les couleurs et le focus de l’ADR 05.
Les touches utilisent toutes `KeyChip` : hauteur 22 dp, lettres A/B/X/Y
rondes, autres touches rectangulaires. Dans chaque groupe d’actions,
ordre de lecture A, B, X, Y, L1, R1, L2, R2, Start ; omettre les touches
inutiles. La navigation L1/R1 reste dans son bandeau, les gâchettes près
du micro. Une touche capturée par les options n’apparaît pas sur la
validation ou sur le champ de consigne.

Les panneaux gardent leurs actions en bas, des espacements réguliers
(8/12/16 dp), des libellés de 12–14 sp et un titre de 18–22 sp. Le texte
principal dicté reste à 25 sp. Le mode compact réduit l’espace, pas le
sens des libellés. Les états vides et de chargement utilisent un texte
centré de 14 sp ; les avis contextuels restent proches de leur action.
Un échec indique ce qui manque et une action possible. Ne pas inventer
un résultat pour remplir un écran vide.
