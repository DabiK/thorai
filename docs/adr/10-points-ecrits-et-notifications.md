# ADR 10 — Points écrits et notifications

Statut : accepté, 14 novembre 2026 (ticket 12).

## Résumés, sans synthèse vocale

La décision utilisateur du 14 novembre remplace toute mention de lecture à voix haute.
Aucun `TextToSpeech` n'est branché dans l'app. Y signifie « Faire le point ».

Nous demandons le bilan à l'agent qui travaille, dans son tour normal. Le domaine du
pont préfixe chaque nouveau tour par une consigne de quatre phrases courtes :
`[Point Régie]`, `Fait :`, `Tests :`, `Suite :`, `Question :`, `[/Point Régie]`.
Les champs doivent s'appuyer sur des observations ; tests absents = « non communiqués ».
Aucun second tour, agent ou appel de modèle n'est lancé pour un résumé. Claude ne
consomme que les quelques tokens de consigne et de bilan de son tour existant.
Les essais de ce ticket utilisent Codex exclusivement. Le texte utilisateur journalisé
reste exact ; la consigne de branche reste transmise, sans gérer les branches.

Alternatives écartées : un petit modèle distant exige clé/réseau/quota supplémentaire ;
un modèle local ajoute téléchargement et mémoire sur la Thor pour une reformulation
que l'agent peut déjà fournir. Le choix n'ajoute ni bibliothèque ni protocole RPC.

`WrittenSummary`, dans le domaine Android, extrait seulement un bilan complet du
texte de l'agent après le dernier message utilisateur. Un nouveau message invalide
le bilan précédent : pas de résultat vert recyclé. Un bilan manquant, incomplet ou
ancien reçoit un point factuel de repli (actions terminées, étape/validation/échec),
avec résultats de tests explicitement non communiqués. Aucun shell réussi n'est
assimilé à une suite de tests réussie. Le fil atelier la source détaillée, y compris
le texte intégral du bilan. Les déclarations d'agent ne sont pas des preuves vérifiées
indépendamment par Régie.

Y depuis la régie ouvre la session sélectionnée et son point ; Y depuis une session
ouvre/ferme le panneau. La fin d'un tour (`idle`), l'échec ou l'arrêt rendent le bilan
visible automatiquement dans la session ; la session reste reprenable. B ferme le
point avant de revenir à la régie. Le point contient trois champs, deux lignes visuelles
au plus chacun, plus titre, attribution et question éventuelle : neuf lignes au maximum. Une ellipse renvoie
au fil complet. Aucun écran n'est automatiquement changé à la fin d'une autre session.

## Notifications

Le port et le cas d'usage des validations (ADR 08) sont conservés. `NotifySessions`
ajoute un port `SessionNotifications` et un adapter Android. Le domaine décide :
- fin de tour/session, échec, question en fin de réponse seulement ;
- aucune notification pour une action ou un morceau de texte ;
- uniquement quand l'écran du haut est en arrière-plan (le bas peut rester visible) ;
- pas d'alerte pour l'historique chargé au démarrage ;
- une alerte par fin, retirée quand la session reprend ; permission/canal refusé permet
  une nouvelle tentative au prochain passage en arrière-plan.

Une question vient du champ explicite du bilan ; pour un ancien agent sans ce format,
repli conservateur sur sa dernière phrase terminée par `?`, hors code en ligne.
Ce n'est pas une compréhension sémantique de toutes les questions possibles. En cas
d'échec, l'erreur prime sur la question. Un texte interrogatif en cours de flux ne sonne pas.

Canal `sessions`, importance normale, contenu privé sur écran verrouillé. Identité
`session:<id>` et action d'intent distincte par session ; `PendingIntent` immuable.
Un toucher ouvre précisément sa session et le point, y compris à froid. Les validations
continuent à ouvrir leur demande exacte dans l'écran de validation. Pas d'action
permettant d'autoriser depuis la notification. La permission Android reste à l'utilisateur.

Les alertes sont observées pendant la vie du processus de Régie, comme celles de
l'ADR 08 : aucun service permanent ni lancement au boot ajouté. Si Android tue l'app,
les fins historiques ne déclenchent pas une rafale au redémarrage ; les validations
encore en attente sont retrouvées. Les bilans se reconstruisent depuis le journal du
pont disponible ; la rétention de 30 jours reste celle du ticket 13.

## Références et interface

API Android consultée via Context7, `library Android` puis `docs /websites/developer_android` :
https://developer.android.com/reference/android/app/Notification.Builder
https://developer.android.com/guide/components/intents-filters
Extraits : `artifacts/screenshots/12/docs-notifications.txt`.

Exports locaux lus : `/tmp/regie-reference{Main,Session}.dc.html`.
Pas d'outil Artifact Claude connecté ; fraîcheur distante non vérifiée. Polices, couleurs,
fil et panneau latéral conservés. Le libellé vocal de la maquette est remplacé par
« Faire le point », selon la décision utilisateur. Pas de maquette dédiée au résumé :
le panneau Y existant est réutilisé et borné, également en mode un écran.


## Relecture et preuves finales

- Quatre régressions rouges puis vertes après le premier commit : le titre tardif ne
  doit pas rouvrir le point fermé (identité du dernier message utilisateur), la question
  doit apparaître aussi dans le panneau, la clôture du bilan doit suivre son ouverture,
  et l'alerte d'échec ne doit pas attendre un journal indisponible.
- Test Compose à la largeur du fil sur la Thor : textes longs et question occupent
  moins de dix lignes réellement mises en page (`TextLayoutResult.lineCount`), pas
  seulement trois chaînes Kotlin. API consultée via Context7 :
  https://developer.android.com/reference/kotlin/androidx/compose/ui/text/TextLayoutResult
- Android : 230 tests et lint verts. Pont : 270 tests, typage/lint verts. Aucun test
  unitaire ne contacte la Thor ou un agent.
- Quatre tours du même Codex réel, aucun Claude : bilan, question, refus et reprise,
  ouverture par Y et par intent à froid, un/deux écrans. Seul `app.regie` installé.
  Version finale : captures 16–18 ; détails dans `artifacts/screenshots/12/essai-thor.txt`.
- Notifications système non démontrées sur la Thor : permission refusée avec
  `USER_FIXED`, importance globale `NONE`. Test Robolectric du vrai adapter et test
  d'ouverture par intent ne remplacent pas la publication et le toucher réels.
  Critère laissé décoché ; statut à fusionner suivant la consigne nocturne.
