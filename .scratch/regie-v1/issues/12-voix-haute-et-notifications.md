# 12 — Points écrits et notifications

Statut : en attente
Bloqué par : 04, 05

## Objectif

Savoir où en sont ses sessions sans tout relire, et être prévenu des décisions à prendre.

**Pas de lecture à voix haute** : l'utilisateur n'en veut pas (14 novembre). La synthèse vocale du module `voice/` n'est pas branchée dans l'app.

## À livrer

- **Résumés courts écrits**, affichés dans l'app :
  - à la fin d'une session : ce qui a été fait, les tests, ce qui reste ;
  - sur demande, avec Y depuis la vue d'ensemble ou une session : un point d'avancement.
  
  Ils sont rédigés à partir de ce que l'agent a exposé. Les générer avec un petit modèle ou les demander à l'agent : à trancher dans un ADR, en épargnant le quota Claude (Codex ou modèle local de préférence).
- **Notifications Android** pour les décisions seulement : validation demandée, question de l'agent, fin de session, échec. Un toucher sur la notification ouvre la bonne session.

## Critères d'acceptation

- Sur la Thor, une validation demandée alors que Régie est en arrière-plan produit une notification qui ouvre la session.
- La fin d'une session affiche son résumé en moins de 10 lignes.
- Y affiche un point d'avancement de la session sélectionnée.
