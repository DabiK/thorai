# Captures et preuves d’essai

`screenshots/NN/` contient les preuves du ticket NN : captures `haut` et `bas`,
résultats de portes, régressions rouges/vertes, journaux et comptes rendus.
Les numéros sont ceux des tickets, indépendants des ADR.

Le README et la notice d’installation utilisent des captures récentes, toutes
ouvertes et inspectées, sans texte inventé : la session et ses réglages
(`22/apres-reglage-applique-*`), la dictée (`25/04-dictee-relue-bas`), Wikipédia
ouvert par Codex (`24/wikipedia-haut`), Thor réelle ; la connexion de Codex
(`23/09-final-simulation-code-*`), banc d’essai avec un code fictif.
[`screenshots/vitrine/`](screenshots/vitrine/) contient `accueil-haut.png`, recadrage de
`26/09-final-clean-haut.png` aux seules sessions de démonstration, et `accueil-bas.png`,
écran du bas de l’accueil pris sur la Thor le 18 novembre.

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
des **copies anonymisées**, pas des sorties brutes à l’octet près. Les espaces
finaux des nouvelles sorties du ticket 20 sont également retirés. Les fixtures ACP
déjà caviardées restent intactes. Les références d’ADR suivent la nouvelle numérotation.

Le contrôle `python3 scripts/repo-audit.py` parcourt le texte versionné et les
nouveaux fichiers non ignorés, avec `rg`. Il ne parcourt pas l’historique Git,
les dépendances ignorées ou le contenu visuel des images. Les domaines d’exemple,
labels Kotlin, l’utilisateur SSH GitHub et un marqueur de contrat explicitement invalide sont des exceptions
documentées dans le script. Aucune valeur détectée n’est imprimée.
