# 21 — Passe sur les textes et l'interface : moins de mots, les bons libellés, une seule voix

Statut : à fusionner
Bloqué par : 20
Possède : `android/` (textes et composants d'interface), `README.md` (captures et libellés cités)

## Objectif

Demande de l'utilisateur, le 14 novembre : retirer le « slop » des textes, harmoniser l'interface, mettre les bons libellés là où il faut, et retirer le texte non nécessaire.

## À livrer

- **Inventaire de tous les textes visibles** : `strings.xml` et textes en dur. Pour chacun : le garder, le raccourcir, le renommer ou le supprimer. La liste avant/après va dans le rapport.
- **Règles d'écriture**, notées dans un court guide `docs/textes.md` :
  - tutoiement ;
  - phrases de 8 mots au plus sur les écrans ;
  - un verbe d'action par bouton (« Envoyer », « Autoriser », « Refuser ») ;
  - pas de jargon technique quand un mot simple existe, pas de formule creuse ou enjouée, pas d'emoji ;
  - pas de texte qui répète ce que l'écran montre déjà ;
  - typographie française (espaces insécables avant « : ? ! » et dans les guillemets).
- **Libellés cohérents partout.**
  - Un même concept porte le même nom sur tous les écrans : session, validation, consigne, projet, mode, modèle, réflexion.
  - Les puces de boutons de la manette (A, B, X, Y, L1, R1, L2, R2, Start) ont le même style et le même ordre partout.
- **Harmonisation visuelle** :
  - espacements, tailles de texte, alignements, états vides, états d'erreur et de chargement ;
  - les deux écrans suivent les mêmes règles ;
  - références : le système visuel de la maquette et l'ADR 05.
- **Texte non nécessaire** : retirer les aides permanentes devenues inutiles, les doublons, et les mentions de debug visibles en build normal.
- **README** : mettre à jour les captures et les libellés qu'il cite.

## Critères d'acceptation

- [x] Captures avant et après de chaque écran, sur l'émulateur, dans `artifacts/screenshots/21/`.
- [x] Aucun texte visible ne contredit le guide `docs/textes.md`. Les tests qui vérifient des libellés sont mis à jour, pas supprimés.
- [x] Les portes de qualité sont vertes.


## Rapport

Textes harmonisés, puces partagées, états vides centrés et portée de X lisible, y compris dans la carte compacte. Guide `docs/textes.md`, README actualisé.

- Inventaire avant/après complet : `artifacts/screenshots/21/inventaire-textes.md` (108 ressources/variantes et 398 textes/fragments uniques). Les données natives et les réponses des agents restent littérales.
- Captures des six écrans et des options : `artifacts/screenshots/21/captures.md`. Scénarios et pas à pas Thor : `artifacts/screenshots/21/essai-emulateur.md`.
- Android : `./gradlew check` vert, 359 tests, aucun ignoré, lint vert. Aucun test supprimé. Rouge/vert sur les libellés, l’ordre des actions, l’état vide, la hauteur de validation, la session disparue et fichier/fichiers.
- Émulateur double écran avec pont Mac et un seul Codex réel ; validation acceptée puis refusée, modifications consultées, dictée WAV locale relue/effacée, modes compact et deux écrans. Aucun Claude lancé.
- Relecture visuelle : doublon Y éliminé, libellé X tronqué corrigé par hauteur minimale, accord fichier/fichiers corrigé. Message de session disparue rétabli après remplacement trop large ; prompts/protocole laissés intacts.
- Décisions réversibles : « Mode », « Modifications », « Revenir », « Faire le point » ; ordre A/B/X/Y et navigation séparée ; puces de 22 dp. Aucun ADR nouveau.
- Thor absente : boutons physiques, micro humain et rendu matériel restent à vérifier selon le protocole. Aucun blocage humain pour cette livraison.
- Hors périmètre : blocage ponctuel Android pendant navigation rapide sur l’émulateur, contourné par relance ; transcription WAV partielle, sans prétendre corriger le moteur vocal.

- Relecture après commit `158c053` : mutation de hauteur détectée par le test (6 tests, 1 échec attendu), fichier restauré puis porte complète verte ; changement superflu d’une entrée de test retiré, capture compacte stabilisée. Preuve `review-mutation-height.txt`.
- Fermeture : pont et agents arrêtés via `bridge.shutdown`, émulateur arrêté (`stop.txt`). Dernière porte verte : 359 tests + lint. Lenteurs de commandes Android à la fermeture consignées dans le rapport, sans cause attribuée.

### Correction de revue — espaces insécables

- Les deux préfixes applicatifs de `ReviewDiff` passent de `Échec :` (U+0020) à `Échec :` (U+00A0). Les deux lignes avant/après de l’inventaire sont corrigées ; les détails externes restent littéraux.
- Deux tests de domaine ajoutés et les assertions de `DiffRecoveryTest` actualisées : quatre échecs attendus avant correction, puis porte Android verte, **361 tests**, zéro échec/erreur/ignoré et lint vert. Preuves : `revision-spacing-red.txt`, `revision-spacing-check.txt`, `revision-tests-count.json` dans `artifacts/screenshots/21/`.
- APK réinstallé sur l’émulateur avec pont Mac ; historique existant consulté, modifications et préparation d’explication vérifiées en deux écrans, modifications en compact. Aucun nouvel agent. Les cas d’erreur précis sont couverts par tests isolés, sans prétendre les reproduire dans ces captures.
- Statut maintenu à `à fusionner`. Aucune décision produit ni nouvel ADR.

- Relecture après `5916902` : aucun autre blocage établi ; fin de trace ADB nettoyée. Porte Android relancée verte, 361 tests. Aucun code changé après la réinstallation vérifiée. Détail dans `essai-emulateur.md`, section « Relecture après 5916902 ».

### POUR L’UTILISATEUR

Suivre les six étapes de [Vérifications avec la Thor](../../../artifacts/screenshots/21/essai-emulateur.md#vérifications-avec-la-thor) : brancher et associer la console, installer l’APK, comparer les six écrans, essayer les boutons physiques, lire une phrase avec L2 puis un ordre avec R2, capturer et quitter. Le rendu matériel, le vrai micro et la manette restent à vérifier ; ne pas lancer Claude ni redémarrer la console.
