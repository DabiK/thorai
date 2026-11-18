# 22 — Session brute comme une app de bureau, écran du bas en table de réglages, accueil en liste

Statut : à fusionner
Possède : `android/` (interface) ; `bridge/` touché pour retirer la demande de bilan (ADR 17)

## Retours de l'utilisateur, 18 novembre (ses mots, résumés)

1. **Retirer toutes les métadonnées de Régie dans le fil** (« Point Régie », « Question », « Aucune suite », « test », « fait », etc.). « L'idée, c'est de répliquer au maximum ce que le harness répond, de façon brute. Pas de métadonnées par-dessus. Je dois me sentir comme dans une application de bureau. »
2. **L'écran du bas d'une session devient une table de réglages du harness**, et non plus une liste de raccourcis.
   - Modèle, niveau de réflexion, mode rapide (fast mode) s'il existe, mode de validation : **toutes les options que l'agent expose** (options de configuration ACP du ticket 17), présentées de façon générique.
   - Le raccourci Y « faire le point » disparaît, puisqu'il est lié au point Régie retiré.
   - Garder L2 (parler), B (retour), X (modifications), L1/R1 et Start.
3. **Dictée (L2)** : l'écran du haut garde toujours le fil, il ne change jamais. **C'est l'écran du bas qui affiche la transcription en direct** pendant que L2 est maintenu.
   - Une fois le message envoyé, retour automatique à la table de réglages, sans écran « message envoyé, B pour revenir ».
   - La relecture avant envoi reste possible sur l'écran du bas (A envoie, B efface), sans changer l'écran du haut.
4. **Accueil en liste**, et non plus en grille de 4 cartes. Une ligne par session, défilable : agent, projet, branche, état (en cours, en attente de consigne, validation demandée…), dernière action, ancienneté. Toutes les informations des cartes, en plus compact.

### Complément transmis par le coordinateur, 18 novembre

À la fin de chaque échange, un « point de session » s'affichait sur l'écran du haut ; l'utilisateur n'en veut pas. Retirer tout ce qui relève du point Régie : le résumé de fin de session, le point d'avancement (Y) et toute carte de synthèse ajoutée par Régie dans le fil. Il ne veut que le fil de la conversation, tel que l'agent le produit. Les notifications Android de décision (validation, fin, échec) peuvent rester, mais aucun résumé ne doit être ajouté au fil.

## Contraintes

- Système visuel de Régie (ADR 05, `docs/textes.md`), sobre, dense, lisible à distance de bras sur un écran de 6 pouces.
- Mettre à jour les tests touchés, pas les supprimer. `./gradlew check` doit être vert.
- Captures avant et après des deux écrans dans `artifacts/screenshots/22/` : sur la Thor si elle est branchée (`scripts/thor-install.sh`, `scripts/thor-shot.sh`), sinon sur l'émulateur (`docs/emulateur.md`, pont du Mac, Codex comme agent).

## Critères d'acceptation

- [x] Le fil ne montre que ce que l'agent produit : messages, outils, sorties, diffs, demandes de validation.
- [x] Pendant une session, on change le modèle ou la réflexion depuis l'écran du bas, sans changer de page.
- [x] L2 → la transcription s'affiche en bas, le haut ne bouge pas ; après A, retour seul aux réglages.
- [x] L'accueil est une liste.

## Rapport

STATUT: à fusionner
RÉSUMÉ: fil brut sans point Régie (pont, domaine et interface), table des réglages ACP en bas, dictée en bas avec retour seul après A, accueil en liste dense (ADR 17).
CRITÈRES:
- [x] Fil brut → panneau « Point de session », Y, étiquettes « Toi », textes de remplacement du panneau Plan/Fichiers retirés ; le pont ne demande plus de bilan ; le bloc `[Point Régie]` des anciens tours est masqué. Thor : bloc présent deux fois dans le journal brut du pont (encore ancien), absent du fil (`apres-session-*`).
- [x] Modèle/réflexion depuis le bas → table générique (Mode, Modèle, Réflexion, Fast mode exposés par Codex) ; Droite ou toucher d'une ligne, croix, A. Réflexion Low → Medium confirmée par Codex sur la Thor (`apres-reglage-*`).
- [x] L2 → haut inchangé, transcription en direct en bas, A puis retour seul à la table, sans écran ni avis pour un nouveau tour (`apres-dictee-*`). Domaine : un message reçu ferme la relecture ; incertain, échec, refus et commentaire gardent leur avis.
- [x] Accueil en liste → une ligne par session (agent, projet, branche, titre, état et étape, demande/erreur/dernière action, ancienneté), focus sur toutes, défilement au focus et au stick (`apres-accueil-*`).
- [x] Complément du coordinateur → résumé de fin, Y et carte de synthèse retirés ; notifications conservées, la fin cite la dernière phrase de l'agent.
PORTE DE QUALITÉ: `cd android && ./gradlew check` vert (domaine 145, app 157, passerelle 77, modèle de commande 3 tests ; lint vert). `cd bridge && npm run check` vert (307 tests, typage, lint ; 2 avertissements existants).
THOR: branchée, APK du ticket installé, vrai Codex (session `hello-world` créée par le pont), L2 par `sendevent` et WAV rejoués, mode un seul écran forcé puis rétabli. Pont de la console non réinstallé (sessions d'autres essais en cours). Déroulé : `artifacts/screenshots/22/essai-thor.md`.
REVIEW: relecture indépendante (sous-agent) de 0087003..HEAD : 9 remarques, toutes corrigées, régressions ajoutées, deux vérifiées rouges par mutation. L2 resté tenu sur l'accueil après fermeture seule, réglages ouvrables sous la dictée en un écran, puces A/X/B de la carte pendant la dictée, avis « en attente » perdu hors session, marqueur cité par l'agent masqué à tort, notification citant le tour précédent, destinataire du panneau en un écran, second A pouvant autoriser une validation, défilement de l'accueil pendant « Quitter ».
SIGNALÉ PAR LE COORDINATEUR: un essai avait laissé `voice_wav=name.wav` actif ; chaque dictée de l'utilisateur renvoyait « Météo du matin ». Corrigé : le rejeu ne sert qu'à la capture suivante (test). Les WAV n'avaient pas été poussés par ce ticket (déjà présents depuis le 14 novembre) ; le coordinateur les a supprimés et a relancé Régie sans extra. L'utilisateur se servant de la console, l'APK final (avec les corrections de relecture) **n'est pas réinstallé** : la Thor garde le build du ticket d'avant relecture, sans rejeu actif.
DÉCISIONS PRISES: ADR 17. Pont modifié bien que hors « Possède » : la demande de bilan est la source du point Régie. Dictée en bas étendue à tous les écrans sur deux écrans (le haut ne change jamais). Masquage du bloc dans le fil pour l'historique. Heure du fil écrite une fois par minute. Plan/Fichiers seulement si exposés. Rappel « A pour appliquer · B pour fermer » retiré. 12 journaux lus d'avance pour la liste (au lieu de 4).
POUR L'UTILISATEUR:
1. Après fusion, réinstaller le pont : `thor/bridge/install.sh` (il ne demande plus de bilan), puis l'app : `scripts/thor-install.sh`, sans extra.
2. Dans une session, maintenir le vrai L2 et parler : le haut ne bouge pas, la transcription s'écrit en bas ; A envoie et la table revient.
3. Droite, choisir Modèle, changer, A : vérifier la confirmation, puis le modèle au tour suivant.
4. Sur l'accueil, descendre la liste à la croix et au stick au-delà des lignes visibles.
QUESTIONS:
- L'ordre vocal R2 « où en est … » (ADR 11) affiche encore un point écrit dans l'écran des ordres, hors du fil. Sans bloc demandé, il montre l'état observé (« bilan non communiqué »). Le garder, le simplifier en dernière phrase de l'agent, ou le retirer ?
- L'heure discrète du fil (demandée au ticket 18) est gardée une fois par minute. La retirer aussi pour un fil encore plus brut ?
CAPTURES: `artifacts/screenshots/22/avant-*`, `apres-accueil-*`, `apres-session-*`, `apres-reglage-*`, `apres-dictee-*`, `apres-un-ecran-*`.
HORS PÉRIMÈTRE: vraie voix humaine au micro et vraie gâchette non essayées (WAV et `sendevent`) ; « avant » de l'accueil pris alors que Chrome occupait le haut (essai d'un autre ticket), la grille reste visible dans les captures 20 et 21.
