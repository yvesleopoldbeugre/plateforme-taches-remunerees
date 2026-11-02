const H = require('./docx-helpers');
const { h1, h2, p, note, bullet, dataTable, imageParagraph, pageBreak } = H;

const sections = [
  ...H.titlePage({
    docType: 'Architecture Technique',
    title: "Plateforme de Tâches & Campagnes Rémunérées",
    subtitle: 'MVP — Choix d\'architecture et justifications',
    project: 'Plateforme de Tâches Rémunérées',
    version: '1.0',
    date: '21 octobre 2026',
    author: 'Document de référence — équipe technique',
  }),
  ...H.tocPage(),

  h1('1. Objet du document'),
  p("Ce document présente l'architecture technique retenue pour le MVP, la démarche de décision suivie (problème → options → choix → justification → trajectoire d'évolution), et la vue d'ensemble des briques logicielles et de leurs interactions. Le détail de l'infrastructure de déploiement et du modèle de données complet est fourni dans le Dossier d'Architecture Technique (DAT)."),

  h1('2. Monolithe modulaire vs microservices'),
  h2('2.1 Problème'),
  p("Comment organiser le déploiement pour supporter quelques milliers d'utilisateurs et plusieurs dizaines d'organisations au lancement, tout en préparant une croissance vers une plateforme de plus grande échelle, sans paralyser la vitesse de développement du MVP ?"),
  h2('2.2 Options'),
  dataTable({
    header: ['Critère', 'Option A — Monolithe modulaire', 'Option B — Microservices'],
    widths: [2400, 3400, 3238],
    rows: [
      ['Vitesse de mise en œuvre', 'Rapide (un seul déploiement, transactions ACID locales)', 'Lente (orchestration, contrats inter-services)'],
      ['Cohérence transactionnelle (wallet, budget)', 'Native via transactions SQL locales', 'Nécessite sagas / 2PC — complexité élevée dès le jour 1'],
      ['Coût opérationnel', 'Faible (1 service à opérer et monitorer)', 'Élevé (N services, observabilité distribuée)'],
      ['Scalabilité horizontale', 'Possible (instances sans état)', 'Plus fine, mais inutile à ce volume'],
      ['Risque principal', 'Couplage si discipline de modularité non respectée', 'Sur-ingénierie, vélocité MVP sacrifiée pour un besoin hypothétique'],
    ],
  }),
  h2('2.3 Choix retenu'),
  p("Option A — Monolithe modulaire (Spring Boot, un seul déployable), avec des frontières de modules strictes au niveau du code (packages par domaine, pas d'accès direct aux entités JPA d'un autre module, communication via services applicatifs ou event bus interne)."),
  h2('2.4 Justification'),
  p("Le volume cible du MVP ne justifie aucun des bénéfices des microservices, alors que leur coût (complexité transactionnelle sur une plateforme financière, où la cohérence du wallet est critique) serait un risque direct sur la fiabilité."),
  h2('2.5 Trajectoire d\'évolution'),
  p("Parce que les modules sont déjà isolés en packages à interfaces explicites, le jour où Wallet/Payments ou Fraud nécessitera une isolation (charge spécifique, équipe dédiée, exigence de conformité séparée), il devient extractible en service indépendant sans réécriture du modèle de domaine — seulement en remplaçant les appels in-process par des appels réseau et l'event bus interne par un vrai broker."),
  pageBreak(),

  h1('3. Vue d\'ensemble de l\'architecture'),
  ...imageParagraph('../diagrams/architecture-technique.png', { width: 600, caption: 'Figure 1 — Architecture technique (monolithe modulaire)' }),
  pageBreak(),

  h1('4. Stack technique et justification brique par brique'),
  dataTable({
    header: ['Brique', 'Usage MVP', 'Justification'],
    widths: [2000, 2800, 4838],
    rows: [
      ['PostgreSQL', 'Base unique, source de vérité, ledger financier', "Transactions ACID indispensables pour l'argent ; contraintes fortes (FK, checks, unique)"],
      ['Redis', 'Cache de lecture, rate limiting, sessions', "Faible coût d'intégration, gain de perf immédiat, utile dès le MVP pour l'anti-fraude"],
      ['Stockage objet S3-compatible', 'Stockage des preuves (photos, PDF, captures)', "Les preuves ne doivent pas être en base (poids, coût) ; URL signées pour accès contrôlé"],
      ['RabbitMQ', 'Introduit à partir de la Phase 8 (paiements/retraits)', "Découple le traitement asynchrone des webhooks et des notifications, sans complexifier les 7 premières phases"],
      ['Elasticsearch / OpenSearch', 'Non retenu en MVP', "Le volume de tâches/campagnes au lancement ne justifie pas un moteur de recherche dédié"],
      ['Kafka / microservices', 'Non retenu en MVP', "Sur-dimensionné tant que le monolithe modulaire suffit"],
    ],
  }),
  note("Principe directeur : commencer simple (PostgreSQL + Redis + stockage objet suffisent pour livrer tout le MVP), ajouter RabbitMQ seulement quand le besoin asynchrone apparaît concrètement en Phase 8, et ne jamais introduire Kafka/Elasticsearch/microservices avant qu'un besoin mesuré ne le justifie."),
  pageBreak(),

  h1('5. Architecture logicielle — règles de dépendance'),
  bullet("Un module ne doit jamais importer directement l'entité JPA d'un autre module pour la modifier (seulement lire via un service applicatif exposé)."),
  bullet("Wallet est un module noyau : Participations, Payments, Withdrawals, Disputes en dépendent, mais Wallet ne dépend d'aucun d'eux (pas de dépendance circulaire)."),
  bullet("Notifications, Fraud, Audit sont des modules transverses passifs : ils s'abonnent aux événements de domaine plutôt que d'être appelés explicitement partout."),
  bullet("Validation dépend de Submissions et Participations, mais ignore tout du Wallet — c'est Participations qui, en recevant la décision de Validation, déclenche le crédit via Wallet."),

  h1('6. Principes d\'architecture à respecter'),
  bullet('Simplicité, modularité, sécurité, traçabilité.'),
  bullet('Cohérence transactionnelle systématique sur les opérations financières.'),
  bullet('Extensibilité et séparation des responsabilités, faible couplage.'),
  bullet('API versionnée dès le lancement.'),
  bullet('Validation métier centralisée (pas de logique métier dans les contrôleurs).'),
  bullet('Opérations financières idempotentes.'),
  h2('À éviter'),
  bullet('Sur-engineering et microservices prématurés.'),
  bullet('Dépendances excessives entre modules.'),
  bullet('Modification destructive des données financières.'),
];

H.buildAndSave(sections, '../05-Architecture-Technique.docx');
