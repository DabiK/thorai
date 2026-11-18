# 24 — Les agents agissent de façon visible sur la console

Statut : à fusionner
Possède : `thor/computer-use/` (consignes, installation)

## Retour de l'utilisateur, 18 novembre

Il a demandé à l'agent d'ouvrir le navigateur. L'agent a appelé `thor_browser` (`browser_navigate`), mais rien n'a changé à l'écran.

## Cause probable

`thor_browser`, c'est Playwright avec un Chromium sans fenêtre dans la Debian : il est invisible par nature. Pour agir sur ce que l'utilisateur voit, l'agent doit passer par `thor_android` (mobile-mcp : ouvrir une appli Android comme Chrome, toucher, saisir) ou par `thor-screen`.

## À livrer

- **Consignes globales** (`thor/computer-use/consignes.md`, posées par `install.sh`) :
  - quand l'utilisateur parle de la console, de l'écran, d'une appli ou « du navigateur », l'agent agit sur Android avec `thor_android` ou `thor-screen`, de façon visible ;
  - `thor_browser` est réservé à l'automatisation web invisible (tests, extraction), et l'agent dit qu'il est invisible quand il s'en sert ;
  - indiquer comment ouvrir une URL dans le navigateur Android : intent `VIEW`, appli par défaut.
- **Essayer si `mobile-mcp` peut ouvrir directement une URL** dans le navigateur Android (outil d'ouverture d'URL ou `am start -a android.intent.action.VIEW -d <url>`), et le documenter.
- **Réinstallation sur la Thor** (`thor/computer-use/install.sh`) si elle est branchée. Sinon, laisser la marche à suivre dans le rapport.

## Critères d'acceptation

- [x] Sur la Thor, depuis une session Codex lancée par Régie, « ouvre le navigateur sur wikipedia.org » ouvre la page dans le navigateur Android, visible à l'écran. → Session `01a1ef0c-dd92-7763-8882-a6523a9e7b50` lancée par le bouton de Régie, message exact seq 7048, appel `thor_android.mobile_open_url` seq 7076–7077 ; `artifacts/screenshots/24/wikipedia-haut.png`.

## Rapport

STATUT: done
RÉSUMÉ: les consignes globales orientent les demandes visibles vers Android et expliquent le caractère invisible de thor_browser.
Ouverture directe d’URL et intent VIEW documentés et essayés ; consignes réinstallées pour les deux agents. Aucun APK réinstallé.
CRITÈRES:
- [x] Wikipédia visible depuis une session Codex lancée par Régie → journal seq 7048–7159 et capture wikipedia-haut.png.
- [x] mobile_open_url essayé, repli VIEW essayé → codex-events.jsonl, intent-view.txt.
- [x] Installation des deux blocs globaux → install-final.txt, empreintes identiques dans installed-final.txt.
PORTE DE QUALITÉ: npm run check vert (308 tests, lint, typage) ; quatre suites shell vertes, bash -n et git diff --check verts.
THOR: Chrome en haut, Régie en bas ; second essai après liste vide, reconnexion autonome puis Wikipédia (final-haut.png). Aucun Claude.
REVIEW: consignes et preuves relues en solo ; récupération de connexion ajoutée et vérifiée sans shell de diagnostic. Détail relecture.md.
DÉCISIONS PRISES: outil mobile_open_url existant, repli VIEW sans paquet imposé ; aucune nouvelle décision durable, aucun ADR.
POUR L'UTILISATEUR: aucun geste requis pour le critère ; pour rejouer : 1. nouvelle session Codex ; 2. vérifier le même projet sur les deux écrans ; 3. envoyer la phrase ; 4. constater Wikipédia puis Quitter quand les autres essais sont terminés.
QUESTIONS: aucune.
CAPTURES: artifacts/screenshots/24/{avant,fiche-codex,probe-url,wikipedia,final}-{haut,bas}.png ; méthode complète dans essai-thor.md.
HORS PÉRIMÈTRE: le bas conservait l’ancienne sélection après création/ouverture ciblée ; le message du premier essai a donc été envoyé par l’API authentifiée à la session lancée dans l’app. Deux tours terminés et annulations ciblées ; sessions idle conservées avec le pont/adapteur partagé, qui n’offre pas de fermeture individuelle. Pas d’arrêt global pendant le ticket 22.
