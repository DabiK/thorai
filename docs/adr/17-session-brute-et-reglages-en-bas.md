# ADR 17 — Session brute, réglages en bas, dictée en bas, accueil en liste

Statut : accepté, 18 novembre 2026 (ticket 22). Remplace la partie « points
écrits » de l'ADR 10 et complète les ADR 05 et 15.

## Contexte

Retours de l'utilisateur du 18 novembre, après essai sur la Thor :
- le fil doit être celui du harness, brut, « comme une application de bureau » :
  ni « Point de session », ni « Question », ni « Fait / Tests / Suite » ;
- l'écran du bas d'une session devient une table de réglages ;
- pendant L2, le haut ne change pas : la transcription s'affiche en bas, puis on
  revient seul aux réglages, sans écran « message envoyé, B pour revenir » ;
- l'accueil passe d'une grille de 4 cartes à une liste dense.

Le coordinateur a transmis un complément : à la fin de chaque échange, le
« point de session » s'affichait en haut. Tout ce qui relève du point Régie est
retiré (résumé de fin, Y, carte de synthèse). Les notifications Android de
décision (validation, fin, échec) restent, sans ajouter de résumé au fil.

## Décisions

### Plus de point Régie

- **Pont** : chaque consigne partait préfixée d'une demande de bilan
  `[Point Régie] … [/Point Régie]` (ADR 10). Elle est retirée : l'agent reçoit les
  mots de l'utilisateur, avec la seule consigne de branche existante. `written-summary.ts`
  est supprimé. Le pont installé sur la Thor doit être réinstallé après fusion
  (`thor/bridge/install.sh`) ; avant cela, les agents ajoutent encore le bloc.
- **Domaine Android** : `RegieReport.strip` masque ce bloc dans le fil, entier ou
  inachevé pendant le flux. Seule la forme exacte est masquée : marqueur seul sur sa
  ligne, lignes Fait / Tests / Suite / Question, marqueur de fin seul sur sa ligne ou
  fin du texte. Un marqueur cité ou discuté par l'agent reste. C'est une demande de Régie, pas une réponse de l'agent :
  le masquer n'est pas réécrire ses données (`docs/textes.md`). Un texte sans le
  marqueur est rendu à l'identique, octet pour octet ; une ligne faite seulement du
  bloc disparaît. L'historique d'avant le ticket est donc propre lui aussi.
- **Interface** : panneau « Point de session », Y en session et sur l'accueil, et
  ouverture du point par notification retirés. Plus d'étiquette « Toi » / « Toi,
  dicté » : la bulle à droite suffit. Le panneau Plan / Fichiers n'apparaît que si
  l'agent expose un plan ou des éditions, sans texte de remplacement. L'heure HH:mm
  de l'ADR 15 reste, mais une seule fois par minute.
- **Notifications** : elles restent (ADR 08 et 10). Une fin de session cite la
  dernière phrase de l'agent dans le dernier tour, sinon « Consulte le fil. », jamais
  un bilan.
  La détection de question garde son repli sur la dernière phrase en « ? ».
- **Gardé hors du fil** : l'ordre vocal R2 « où en est … » (ADR 11) affiche
  toujours `WrittenSummary` dans l'écran des ordres. Sans bloc, il retombe sur
  l'état observé. Tranché le 18 novembre (ticket 25, ADR 19) : il ouvre la session
  visée, sans point écrit.

### Écran du bas d'une session : la table des réglages

- La table est toujours affichée sous les voisines L1/R1 : le mode de validation de
  Régie, puis chaque option ACP que l'agent expose (modèle, réflexion, mode rapide,
  autres sélecteurs), avec ses noms, sans catalogue dans l'app. Les règles du
  ticket 17 sont inchangées : `mode` et `collaboration_mode` restent derrière le
  mode de Régie, une valeur n'est confirmée que par l'accusé de l'agent.
- Droite, « → Modifier » ou un toucher sur une ligne fait passer la table en
  modification, sur la ligne touchée (`ReviewSessionSettings.open(id, row)`).
  Croix pour choisir et changer, A applique, B referme ; une valeur pas encore
  appliquée est en ambre. Le rappel « A pour appliquer · B pour fermer » est retiré :
  les touches le montrent déjà.
- Touches hors modification, dans l'ordre B, X, L2, Start ; Start seulement si un
  tour peut être interrompu. « L2 Parler » se maintient aussi au doigt. Une
  validation en attente prend la place des touches, comme à l'ADR 15.
- La barre « → Régler la session · Régler la voix » disparaît des sessions ;
  « Régler la voix » reste sur la dictée et les autres écrans.
- Un seul écran : inchangé, le résumé des réglages reste sous l'en-tête et Droite
  ouvre le panneau de 260 dp.

### Dictée

- Deux écrans : le haut ne change jamais, quel que soit l'écran (session, accueil,
  modifications, validation). Le bas montre le destinataire, la transcription en
  direct à 25 sp, le niveau du micro et A/B/X/Y. Choix étendu aux autres écrans par
  cohérence : le haut garde ce que l'on commente ou refuse.
- Un seul écran : dans une session, le fil reste et la dictée prend le panneau du
  bas ; ailleurs, la dictée plein écran est conservée.
- Règle du domaine (`ReviewDictation`) : un **message** que l'agent a reçu (nouveau
  tour, consigne en cours de tour ou mise en attente) ferme seul la relecture et
  transmet son reçu. Un nouveau tour n'a pas d'avis, le fil le montre ; une consigne
  en cours ou en attente le dit brièvement en bas. Hors de sa session (dictée depuis
  l'accueil), seul un nouveau tour ferme : une consigne en cours ou en attente garde
  son avis jusqu'à B. Un envoi incertain, un échec, un refus avec consigne ou un
  commentaire de modifications gardent aussi leur avis jusqu'à B.
- La fermeture seule fait le même ménage que B (gâchettes tenues, défilement de la
  transcription). Un second A dans les 500 ms qui suivent est ignoré : il ne doit pas
  autoriser une validation arrivée pendant la relecture. Les réglages ne s'ouvrent pas
  pendant une dictée ou un ordre ; la carte de validation du fil garde ses touchers
  sans puces A/X/B pendant la dictée (même règle que pour les options, ADR 15).
- Le rejeu WAV de debug (`--es voice_wav`) ne sert qu'à la capture suivante : un
  essai laissé actif renvoyait sa phrase à chaque dictée de l'utilisateur.

### Accueil en liste

- Une ligne par session, défilable : agent, projet et branche, titre, état et étape,
  demande en attente, erreur ou dernière action, ancienneté. Ce qui n'est pas exposé
  reste absent (plus de « Plan non communiqué »).
- Toutes les lignes prennent le focus. La croix monte et descend d'une ligne, sans
  boucler ; gauche et droite ne font rien. L1/R1 et la priorité de R1 aux
  validations sont inchangés. La liste ne défile que si la ligne quitte l'écran.
  Le stick droit la fait défiler sans déplacer le focus.
- La passerelle lit d'avance le journal des 12 premières sessions
  (`OverviewList.PRELOADED`, contre 4 cartes) pour l'étape et la dernière action ;
  les autres les montrent une fois ouvertes.

## Vérification

- Tests d'abord dans le domaine : bloc masqué entier ou inachevé, ligne vide retirée,
  notification sans bilan, ligne touchée ouverte, fermeture seule après livraison,
  reçu transmis. Tests Compose mis à jour : fil brut, table générique, touches B X L2
  Start, dictée en bas, liste et défilement au focus. Pont : consigne transmise telle
  quelle.
- Relecture indépendante après commit : neuf remarques, toutes corrigées avec
  régression (L2 resté tenu sur l'accueil, réglages invisibles sous la dictée, puces de
  la carte, avis perdu hors session, marqueur cité masqué, notification du tour
  précédent, destinataire en un écran, second A, défilement pendant « Quitter »).
- Thor, vrai Codex : bloc présent dans le journal du pont encore ancien, absent du
  fil ; réflexion changée et confirmée depuis le bas ; L2 au contrôleur (`sendevent`)
  avec un WAV rejoué : haut inchangé, transcription en bas, A puis retour seul à la
  table ; mode un seul écran forcé. Captures `artifacts/screenshots/22/`.
