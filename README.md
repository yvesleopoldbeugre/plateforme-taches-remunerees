# Plateforme de Tâches & Campagnes Rémunérées

Marketplace de micro-tâches et de campagnes rémunérées (MVP), contexte Afrique de l'Ouest.

## Structure du dépôt

- [`docs/`](docs/) — dossier de conception complet : cahiers des charges fonctionnel et
  technique, diagrammes de cas d'utilisation et de séquence, architecture technique, DAT,
  user stories. Voir [`docs/sources/`](docs/sources/) pour les scripts de génération.
- [`backend/`](backend/) — backend Spring Boot (monolithe modulaire). Voir
  [`backend/README.md`](backend/README.md) pour le démarrage local.

## Avancement (roadmap DAT §11)

- [x] Phase 1 — Architecture et base technique
- [x] Phase 2 — Authentification et utilisateurs
- [x] Phase 3 — Organisations
- [x] Phase 4 — Campagnes et tâches
- [x] Phase 5 — Participations et soumissions
- [x] Phase 6 — Validation
- [x] Phase 7 — Wallet et ledger
- [x] Phase 8 — Paiements et retraits
- [x] Phase 9 — Administration
- [x] Phase 10 — Anti-fraude
- [x] Phase 11 — Statistiques

Les 11 phases de la roadmap du DAT sont closes — voir [`backend/README.md`](backend/README.md)
pour le détail phase par phase et ce qui reste explicitement hors scope du MVP (litiges, MFA,
BR-044, BR-050, KYC utilisateur, provider de paiement réel...).
