# Captures et preuves d’essai

`screenshots/NN/` contient les preuves du ticket NN : captures `haut` et `bas`,
résultats de portes, régressions rouges/vertes, journaux et comptes rendus.
Les numéros sont ceux des tickets, indépendants des ADR.

La vitrine utilise uniquement [les deux captures de session du ticket 20](screenshots/20/),
sur un projet de démonstration et un émulateur à deux écrans, avec un vrai Codex.
Elles ont été ouvertes et inspectées ; aucun montage ni texte inventé.

Les anciennes captures illustrent la version du ticket au moment de son essai ;
elles ne décrivent pas toutes l’interface actuelle. Les exports XML, WAV et clients
manuels conservés servent à reproduire ces campagnes, pas aux tests unitaires.
Les sorties identiques de plusieurs installations ne sont pas des essais supprimables :
elles accompagnent des campagnes distinctes. Les fichiers vides attestant l’absence
de processus après Quitter sont conservés pour la même raison.

Les journaux de compilation transitoires non référencés
`07/controls-compilation-intermediate.txt`, `15/intermediate-ui.txt` et
`15/intermediate-new-form.txt` ont été retirés. Les régressions rouges/vertes
et portes finales de ces tickets restent conservées.

Au ticket 20, les chemins de comptes dans les textes ont été remplacés par
`/home/USER` et les chemins de cache privés par des exemples neutres. Les codes
de retour, numéros d’événements et assertions restent inchangés ; ces textes sont
des **copies anonymisées**, pas des sorties brutes à l’octet près. Les fixtures ACP
déjà caviardées restent intactes. Les références d’ADR suivent la nouvelle numérotation.

Le contrôle `python3 scripts/repo-audit.py` parcourt le texte versionné et les
nouveaux fichiers non ignorés, avec `rg`. Il ne parcourt pas l’historique Git,
les dépendances ignorées ou le contenu visuel des images. Les domaines d’exemple,
labels Kotlin, l’utilisateur SSH GitHub et un marqueur de contrat explicitement invalide sont des exceptions
documentées dans le script. Aucune valeur détectée n’est imprimée.
