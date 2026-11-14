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

- [x] R2 séparé de L2, règles françaises puis proposition de fonction Mistral : domaine,
  adapter HTTP isolé, tests sans service/agent/console. ADR 11.
- [x] A confirme une seule fois ; B annule ; cible figée et validation revérifiée.
  `VoiceCommandsTest`, transport avec faux pont, Thor : validation réelle encore en
  attente après vingt WAV, puis refus confirmé et double A sans seconde réponse.
- [x] Ordre inconnu visible sans effet : tests, captures 06/08/12/16/19/20, A sans effet.
- [x] Installation `app.regie`, qualité Android verte, revue solo (aucun sous-agent).
- [ ] Lecture des vingt ordres au vrai micro par l'utilisateur et appel Mistral réel :
  permission/voix/clé à fournir au matin, aucune permission ni clé accordée cette nuit.

Premier corpus WAV Thomas : **14/20 (70 %)**, résultats bruts conservés. Les noms
techniques mal transcrits restent non compris, sans cible approchée. Ce taux ne
remplace pas le critère utilisateur. Taux final et relecture ajoutés ci-dessous.

Porte : `cd android && ./gradlew check` verte. Lancement minimal en mode plan,
confirmation de branche/consigne ; éditeur complet de fiche réservé au ticket 11.
Pas de changement du pont, de la voix, de branche git, fusion ou push. Statut à
fusionner selon la consigne nocturne, avec critère humain explicitement non tenu.
Pas à pas et captures : `artifacts/screenshots/09/essai-thor.txt`.
