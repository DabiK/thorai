# 09 — Ordres vocaux (R2)

Statut : à fusionner
Bloqué par : 08

## Objectif

R2 maintenu donne un ordre plutôt qu'un message à l'agent.

## À livrer

- **Ordres reconnus** : lancer une session, faire le point (sur une session ou sur toutes), approuver ou refuser la validation affichée, mettre en pause ou tout mettre en pause, passer à une session par son nom.
- **Interprétation** : des règles fixes pour les tournures courantes, puis un petit modèle de langage avec appel de fonctions quand la phrase est ambiguë. Le fournisseur est à choisir et à noter dans un ADR.
- **Confirmation** : tout ordre qui agit se confirme par A. « Approuve » sans A n'approuve rien.
- **Ordre non compris** : il est reformulé à l'écran et ne fait rien.

## Critères d'acceptation

- [ ] Sur la Thor, vingt ordres préparés, lus par l'utilisateur, sont bien interprétés. Taux noté dans l'ADR.

## Rapport

- [x] R2 distinct de L2 : règles fixes et repli par appel de fonction Ministral 3B,
  port/adapters séparés, ADR 11. Aucun service/agent/console dans les tests unitaires.
- [x] Ordres reconnus : lancement minimal en plan, points écrits individuels/globaux,
  autorisation unique/refus, pause individuelle/globale et navigation par nom unique.
  Corpus de vingt textes dans `VoiceCommandsTest`, effets du domaine et transport
  testés ; vrais effets Codex sur Thor (captures 21 et 25–33).
- [x] A obligatoire, double A protégé, validation et cible figées puis revérifiées.
  Demandes réelles inchangées sans A ; refus seq 2617, autorisation seq 2645,
  interruption seq 2652. Aucun ordre de contrôle ajouté aux messages de l'agent.
- [x] Ordre inconnu affiché sans effet : captures et tests, A ne fait rien.
- [x] Porte Android : **250 tests**, zéro échec/ignoré, lint vert ; APK réinstallé,
  deux écrans et mode un écran, manette et maintien tactile Redire vérifiés.
- [x] Relecture solo : quatre régressions rouges puis vertes (variantes ASR,
  condition « si », maintien tactile, réponse LLM tronquée), règle de pause dans
  le domaine ; `git diff main...HEAD` et captures relus. Aucun sous-agent.
- [ ] Vingt ordres lus au vrai micro par l'utilisateur : pas encore mesuré.
- [ ] Mistral réel : clé à saisir par l'utilisateur, contrat testé sur faux HTTP.

Corpus Thomas sur le moteur local réel : **14/20 initial**, **15/20 final**. Au final,
les variantes de pause passent ; quatre noms restent mal transcrits et un essai
à froid est vide. Reprise séparée de ce dernier réussie (23–24), sans modifier le
score. Aucun taux humain revendiqué ; ADR 11 contient les mesures et leurs limites.

Deux sessions Codex seulement ; aucun Claude. `Quitter` arrête le pont et les agents,
forward/WAV retirés. Aucune permission accordée, aucun redémarrage. Aucun push/fusion.
Permissions précédentes, transcription des noms et arrêt des sous-processus Codex
signalés ; fiche complète/correction/niveau/base de branche restent au ticket 11.

**À faire avec l'utilisateur** : autoriser le micro, lire `corpus.txt` en adaptant
les noms affichés, noter l'action avant A et le taux humain dans l'ADR, vérifier
A/B et les effets, puis saisir sa clé Mistral s'il veut le repli cloud. Pas à pas
complet et preuves : `artifacts/screenshots/09/essai-thor.txt`, captures 23–35 et
`final-corpus/`. Statut à fusionner selon la consigne nocturne ; critère humain décoché.
