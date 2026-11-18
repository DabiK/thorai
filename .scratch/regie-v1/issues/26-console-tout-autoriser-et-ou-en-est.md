# 26 — Session console en « Tout autoriser », « où en est » sans confirmation

Statut : prêt
Possède : `android/`

## Décisions de l'utilisateur, 18 novembre (questions du ticket 25)

1. La session « console » (Y sur l'accueil) se crée par défaut en mode **« Tout autoriser »** : aucune demande de validation pendant que l'agent pilote la console. Le mode reste modifiable dans la fiche et dans les réglages de la session.
2. L'ordre vocal R2 **« où en est … »** ouvre directement la session visée, ou la session active, **sans relecture ni A**. Ouvrir une session ne change rien, donc aucune confirmation n'est nécessaire. Les ordres qui agissent (lancer, approuver, mettre en pause…) gardent leur confirmation par A.

## Critères d'acceptation

- Tests de domaine mis à jour pour les deux points. `./gradlew check` est vert.
- Sur la Thor : Y puis A crée une session console en « Tout autoriser », et la table du bas l'affiche. R2 « où en est la session console » ouvre la session sans A.
