# Décisions techniques

Les ADR sont numérotés dans leur ordre de rédaction, indépendamment des tickets.
Au ticket 20, les anciens ADR **18 et 19 deviennent 15 et 16** ; les références
du dépôt sont mises à jour. Les numéros des tickets et des captures restent inchangés.
Un ajout daté ou une décision du journal prime sur le contexte historique d’un ADR.

| ADR | Sujet | Ticket d’origine |
| --- | --- | --- |
| [01](01-architecture-et-reutilisation.md) | Architecture et réutilisation | Cadrage |
| [02](02-pilotage-des-agents.md) | Pont ACP, modes, branches, options | 02, complété par 13, 15, 17 |
| [03](03-acces-des-agents-a-android.md) | Accès libre à Android | 03, révisé par 14 |
| [04](04-voix.md) | Ports et laboratoire voix | 04 |
| [05](05-socle-android.md) | Deux écrans et manette | 01, complété par 15 |
| [06](06-passerelle-vers-le-pont.md) | Connexion, démarrage et arrêt | 05 |
| [07](07-session-en-direct.md) | Fil et livraison des messages | 06 |
| [08](08-validations.md) | Validations et notifications | 07 |
| [09](09-dictee-dans-l-app.md) | Dictée relue, locale par défaut | 08 |
| [10](10-points-ecrits-et-notifications.md) | Points écrits (remplacés par 17), notifications | 12 |
| [11](11-ordres-vocaux.md) | Ordres vocaux, confirmés par A sauf la navigation | 09, complété par 26 |
| [12](12-relecture-du-diff.md) | Éditions et explications | 10 |
| [13](13-fiche-nouvelle-session.md) | Fiche et nouveau projet | 11, complété par 15 |
| [14](14-banc-emulateur.md) | Banc Mac et deux écrans | 16 |
| [15](15-session-tout-en-un.md) | Validations dans le fil, options en bas | 18 |
| [16](16-configuration-native-des-agents.md) | MCP, consignes et permissions natives | 19 |
| [17](17-session-brute-et-reglages-en-bas.md) | Fil brut, réglages et dictée en bas, accueil en liste | 22 |
| [18](18-connexion-des-agents.md) | Code d’appareil Codex, connexion et repli Claude | 23 |
| [19](19-mode-console.md) | L2 depuis toute appli, message volant, session console | 25, complété par 26 |
| [20](20-partage-et-release.md) | Installation Termux autonome et release signée | 27 |

Le [journal](00-journal-de-reprise.md) conserve les campagnes successives, leurs
limites et les décisions produit. Le statut courant se lit dans les fichiers
des [tickets](../../.scratch/regie-v1/issues), pas dans une ancienne ligne du journal.
