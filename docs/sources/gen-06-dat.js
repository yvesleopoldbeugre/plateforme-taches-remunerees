const H = require('./docx-helpers');
const { h1, h2, h3, p, note, bullet, numbered, dataTable, imageParagraph, pageBreak, codeBlock } = H;

const ddlAuth = `CREATE TABLE users (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email             VARCHAR(255) NOT NULL UNIQUE,
    phone             VARCHAR(32)  NOT NULL UNIQUE,
    password_hash     VARCHAR(255) NOT NULL,
    status            VARCHAR(32)  NOT NULL DEFAULT 'PENDING_VERIFICATION',
    email_verified_at TIMESTAMPTZ,
    phone_verified_at TIMESTAMPTZ,
    mfa_enabled       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_users_status CHECK (status IN ('PENDING_VERIFICATION','ACTIVE','SUSPENDED','BANNED'))
);
CREATE INDEX idx_users_status ON users(status);

CREATE TABLE user_profiles (
    user_id         UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    full_name       VARCHAR(255) NOT NULL,
    birth_date      DATE,
    gender          VARCHAR(16),
    country         VARCHAR(2) NOT NULL,
    city            VARCHAR(128),
    language        VARCHAR(8) NOT NULL DEFAULT 'fr',
    reputation_score NUMERIC(5,2) NOT NULL DEFAULT 0,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);`;

const ddlOrgs = `CREATE TABLE organizations (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(255) NOT NULL,
    legal_name    VARCHAR(255),
    country       VARCHAR(2) NOT NULL,
    status        VARCHAR(32) NOT NULL DEFAULT 'PENDING_VERIFICATION',
    kyc_status    VARCHAR(32) NOT NULL DEFAULT 'NOT_SUBMITTED',
    created_by    UUID NOT NULL REFERENCES users(id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_org_status CHECK (status IN ('PENDING_VERIFICATION','ACTIVE','SUSPENDED'))
);

CREATE TABLE organization_members (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users(id),
    role            VARCHAR(32) NOT NULL,
    status          VARCHAR(32) NOT NULL DEFAULT 'INVITED',
    invited_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    joined_at       TIMESTAMPTZ,
    UNIQUE (organization_id, user_id),
    CONSTRAINT chk_member_role CHECK (role IN ('OWNER','MANAGER'))
);`;

const ddlCampaigns = `CREATE TABLE campaigns (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id  UUID NOT NULL REFERENCES organizations(id),
    title            VARCHAR(255) NOT NULL,
    description      TEXT,
    category         VARCHAR(64) NOT NULL,
    status           VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    budget_total     NUMERIC(14,2) NOT NULL,
    budget_reserved  NUMERIC(14,2) NOT NULL DEFAULT 0,
    budget_consumed  NUMERIC(14,2) NOT NULL DEFAULT 0,
    currency         VARCHAR(3) NOT NULL DEFAULT 'XOF',
    start_date       TIMESTAMPTZ NOT NULL,
    end_date         TIMESTAMPTZ NOT NULL,
    created_by       UUID NOT NULL REFERENCES users(id),
    version          INTEGER NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_campaign_status CHECK (status IN
        ('DRAFT','PENDING_REVIEW','REJECTED','PUBLISHED','PAUSED','FULL','COMPLETED','EXPIRED','CANCELLED')),
    CONSTRAINT chk_campaign_dates CHECK (end_date > start_date)
);
CREATE INDEX idx_campaigns_org_status ON campaigns(organization_id, status);
CREATE INDEX idx_campaigns_status_dates ON campaigns(status, end_date);

CREATE TABLE campaign_eligibility_criteria (
    campaign_id    UUID PRIMARY KEY REFERENCES campaigns(id) ON DELETE CASCADE,
    min_age        SMALLINT,
    max_age        SMALLINT,
    gender         VARCHAR(16),
    gender_justification TEXT,
    countries      TEXT[],
    languages      TEXT[],
    min_reputation NUMERIC(5,2) DEFAULT 0,
    CONSTRAINT chk_gender_justified CHECK (gender IS NULL OR gender_justification IS NOT NULL)
);

CREATE TABLE tasks (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id             UUID NOT NULL REFERENCES campaigns(id) ON DELETE CASCADE,
    type                    VARCHAR(64) NOT NULL,
    title                   VARCHAR(255) NOT NULL,
    instructions            TEXT NOT NULL,
    reward_amount           NUMERIC(10,2) NOT NULL CHECK (reward_amount > 0),
    max_participants        INTEGER NOT NULL CHECK (max_participants > 0),
    current_participants    INTEGER NOT NULL DEFAULT 0,
    expected_proof_type     VARCHAR(32) NOT NULL,
    auto_validation_rules   JSONB,
    status                  VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    reservation_ttl_minutes INTEGER NOT NULL DEFAULT 1440,
    version                 INTEGER NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_task_participants CHECK (current_participants <= max_participants)
);
CREATE INDEX idx_tasks_campaign ON tasks(campaign_id);
CREATE INDEX idx_tasks_type_status ON tasks(type, status);`;

const ddlParticipations = `CREATE TABLE task_participations (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id       UUID NOT NULL REFERENCES tasks(id),
    user_id       UUID NOT NULL REFERENCES users(id),
    status        VARCHAR(32) NOT NULL DEFAULT 'RESERVED',
    reserved_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at    TIMESTAMPTZ NOT NULL,
    submitted_at  TIMESTAMPTZ,
    resolved_at   TIMESTAMPTZ,
    CONSTRAINT chk_participation_status CHECK (status IN
        ('RESERVED','IN_PROGRESS','SUBMITTED','UNDER_REVIEW','CORRECTION_REQUESTED',
         'APPROVED','REJECTED','DISPUTED','CANCELLED','EXPIRED'))
);
CREATE UNIQUE INDEX uq_active_participation ON task_participations(task_id, user_id)
    WHERE status IN ('RESERVED','IN_PROGRESS','SUBMITTED','UNDER_REVIEW','CORRECTION_REQUESTED');
CREATE INDEX idx_participations_user ON task_participations(user_id, status);
CREATE INDEX idx_participations_expiry ON task_participations(status, expires_at);

CREATE TABLE task_submissions (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    participation_id UUID NOT NULL REFERENCES task_participations(id),
    content          JSONB NOT NULL,
    content_hash     VARCHAR(64) NOT NULL,
    attachments      JSONB,
    submitted_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_submissions_hash ON task_submissions(content_hash);
CREATE INDEX idx_submissions_participation ON task_submissions(participation_id);

CREATE TABLE validations (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    submission_id UUID NOT NULL REFERENCES task_submissions(id),
    decision      VARCHAR(32) NOT NULL,
    reason_code   VARCHAR(64),
    reviewed_by   UUID REFERENCES users(id),
    comment       TEXT,
    reviewed_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_validation_decision CHECK (decision IN
        ('AUTO_APPROVED','APPROVED','REJECTED','CORRECTION_REQUESTED'))
);

CREATE TABLE rewards (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    participation_id UUID NOT NULL UNIQUE REFERENCES task_participations(id),
    amount           NUMERIC(10,2) NOT NULL,
    currency         VARCHAR(3) NOT NULL DEFAULT 'XOF',
    status           VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    credited_at      TIMESTAMPTZ,
    CONSTRAINT chk_reward_status CHECK (status IN ('PENDING','CREDITED','CANCELLED'))
);`;

const ddlWallet = `CREATE TABLE wallets (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_type        VARCHAR(16) NOT NULL,
    owner_id          UUID NOT NULL,
    available_balance NUMERIC(14,2) NOT NULL DEFAULT 0,
    pending_balance   NUMERIC(14,2) NOT NULL DEFAULT 0,
    reserved_balance  NUMERIC(14,2) NOT NULL DEFAULT 0,
    lifetime_earned   NUMERIC(14,2) NOT NULL DEFAULT 0,
    lifetime_withdrawn NUMERIC(14,2) NOT NULL DEFAULT 0,
    currency          VARCHAR(3) NOT NULL DEFAULT 'XOF',
    version           INTEGER NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (owner_type, owner_id),
    CONSTRAINT chk_wallet_balance CHECK (available_balance >= 0 AND reserved_balance >= 0)
);

CREATE TABLE wallet_transactions (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id        UUID NOT NULL REFERENCES wallets(id),
    type             VARCHAR(32) NOT NULL,
    amount           NUMERIC(14,2) NOT NULL,
    balance_after    NUMERIC(14,2) NOT NULL,
    reference_type   VARCHAR(32) NOT NULL,
    reference_id     UUID,
    idempotency_key  VARCHAR(128) NOT NULL UNIQUE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
    -- INSERT-ONLY : pas d'UPDATE/DELETE applicatif (appliqué par rôle DB + revoke)
);
CREATE INDEX idx_wallet_tx_wallet ON wallet_transactions(wallet_id, created_at);
CREATE INDEX idx_wallet_tx_reference ON wallet_transactions(reference_type, reference_id);`;

const ddlPayments = `CREATE TABLE payment_methods (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_type       VARCHAR(16) NOT NULL,
    owner_id         UUID NOT NULL,
    provider         VARCHAR(32) NOT NULL,
    type             VARCHAR(32) NOT NULL,
    masked_identifier VARCHAR(64) NOT NULL,
    is_default       BOOLEAN NOT NULL DEFAULT FALSE,
    status           VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE payments (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id    UUID NOT NULL REFERENCES organizations(id),
    payment_method_id  UUID REFERENCES payment_methods(id),
    provider           VARCHAR(32) NOT NULL,
    provider_reference VARCHAR(128),
    amount             NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    status             VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    idempotency_key    VARCHAR(128) NOT NULL UNIQUE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at       TIMESTAMPTZ,
    CONSTRAINT chk_payment_status CHECK (status IN ('PENDING','COMPLETED','FAILED'))
);

CREATE TABLE withdrawals (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID NOT NULL REFERENCES users(id),
    wallet_id          UUID NOT NULL REFERENCES wallets(id),
    payment_method_id  UUID NOT NULL REFERENCES payment_methods(id),
    amount             NUMERIC(14,2) NOT NULL,
    fees               NUMERIC(14,2) NOT NULL DEFAULT 0,
    net_amount         NUMERIC(14,2) NOT NULL,
    status             VARCHAR(32) NOT NULL DEFAULT 'REQUESTED',
    idempotency_key    VARCHAR(128) NOT NULL UNIQUE,
    requested_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at       TIMESTAMPTZ,
    CONSTRAINT chk_withdrawal_status CHECK (status IN
        ('REQUESTED','UNDER_REVIEW','APPROVED','PROCESSING','COMPLETED','FAILED','REJECTED','CANCELLED'))
);
CREATE INDEX idx_withdrawals_status ON withdrawals(status, requested_at);`;

const ddlOther = `CREATE TABLE disputes (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    participation_id UUID NOT NULL UNIQUE REFERENCES task_participations(id),
    opened_by        UUID NOT NULL REFERENCES users(id),
    status           VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    reason           TEXT NOT NULL,
    resolution       VARCHAR(32),
    resolved_by      UUID REFERENCES users(id),
    resolved_at      TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_dispute_status CHECK (status IN ('OPEN','UNDER_REVIEW','RESOLVED'))
);

CREATE TABLE notifications (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_type VARCHAR(16) NOT NULL,
    recipient_id   UUID NOT NULL,
    event_type     VARCHAR(64) NOT NULL,
    channel        VARCHAR(16) NOT NULL,
    status         VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    payload        JSONB,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at        TIMESTAMPTZ,
    read_at        TIMESTAMPTZ
);
CREATE INDEX idx_notifications_recipient ON notifications(recipient_type, recipient_id, status);

CREATE TABLE fraud_events (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type          VARCHAR(64) NOT NULL,
    risk_level    VARCHAR(16) NOT NULL,
    user_id       UUID REFERENCES users(id),
    campaign_id   UUID REFERENCES campaigns(id),
    detected_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    reason        TEXT NOT NULL,
    status        VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    admin_decision TEXT,
    reviewed_by   UUID REFERENCES users(id),
    reviewed_at   TIMESTAMPTZ,
    CONSTRAINT chk_fraud_status CHECK (status IN ('OPEN','REVIEWED','CONFIRMED','DISMISSED'))
);
CREATE INDEX idx_fraud_user ON fraud_events(user_id, status);

CREATE TABLE audit_logs (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_type    VARCHAR(16) NOT NULL,
    actor_id      UUID,
    action        VARCHAR(128) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id   UUID,
    before_state  JSONB,
    after_state   JSONB,
    ip_address    INET,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_resource ON audit_logs(resource_type, resource_id);
CREATE INDEX idx_audit_actor ON audit_logs(actor_type, actor_id, created_at);`;

const erd = `User (1) --< UserProfile (1)
User (1) --< OrganizationMember >-- (1) Organization
Organization (1) --< Campaign
Organization (1) --(1) Wallet [owner_type=ORGANIZATION]
User (1) --(1) Wallet [owner_type=USER]
Campaign (1) --< CampaignEligibilityCriteria (1)
Campaign (1) --< Task
Task (1) --< TaskParticipation >-- (1) User
TaskParticipation (1) --< TaskSubmission
TaskSubmission (1) --< Validation
TaskParticipation (1) --(1) Reward
TaskParticipation (1) --(0..1) Dispute
Wallet (1) --< WalletTransaction
Organization (1) --< Payment
User (1) --< Withdrawal >-- (1) Wallet
User/Organization (1) --< PaymentMethod
(*) --< Notification
(*) --< FraudEvent
(*) --< AuditLog`;

const sections = [
  ...H.titlePage({
    docType: "Dossier d'Architecture Technique",
    title: "Plateforme de Tâches & Campagnes Rémunérées",
    subtitle: 'MVP — DAT',
    project: 'Plateforme de Tâches Rémunérées',
    version: '1.0',
    date: '21 octobre 2026',
    author: 'Document de référence — équipe technique',
  }),
  ...H.tocPage(),

  h1('1. Introduction'),
  h2('1.1 Objet et portée du DAT'),
  p("Ce Dossier d'Architecture Technique (DAT) est le document de référence exhaustif pour l'équipe de développement : il détaille le modèle de données complet (DDL PostgreSQL, contraintes, index), la topologie d'infrastructure, les mécanismes de concurrence et d'intégrité financière, la stratégie de sécurité, de sauvegarde, d'observabilité et de tests. Il s'appuie sur les choix présentés dans les documents « Architecture Technique » et « Cahier des Charges Technique »."),
  h2('1.2 Documents liés'),
  bullet('Cahier des Charges Fonctionnel — périmètre métier et règles de gestion.'),
  bullet('Cahier des Charges Technique — exigences techniques de synthèse.'),
  bullet("Diagramme de Cas d'Utilisation et Diagramme de Séquence — vues UML complémentaires."),
  bullet('Architecture Technique — justification du choix monolithe modulaire et vue applicative.'),
  pageBreak(),

  h1('2. Architecture applicative'),
  p("Rappel de l'architecture applicative (détaillée et justifiée dans le document « Architecture Technique ») : un monolithe modulaire Spring Boot structuré par domaine métier, avec un module noyau Wallet sans dépendance vers les autres modules métier, et des modules transverses passifs (Notifications, Fraud, Audit) abonnés à un event bus interne."),
  ...imageParagraph('../diagrams/architecture-technique.png', { width: 560, caption: 'Figure 1 — Architecture applicative' }),
  pageBreak(),

  h1("3. Architecture d'infrastructure et déploiement"),
  p("La plateforme est déployée sur une infrastructure à région unique au stade MVP, avec redondance applicative et réplication base de données en lecture. Le CDN/WAF filtre le trafic entrant avant qu'il n'atteigne le load balancer, qui distribue la charge vers des instances applicatives sans état. Le sous-réseau de données n'est accessible que depuis le sous-réseau applicatif."),
  ...imageParagraph('../diagrams/deploiement-infrastructure.png', { width: 600, caption: 'Figure 2 — Diagramme de déploiement' }),
  h2('3.1 Environnements'),
  p("Trois environnements distincts (développement, staging, production) répliquent cette même topologie, isolés au niveau réseau et données. Le staging sert de dernière validation avant mise en production, avec un jeu de données anonymisé représentatif."),
  h2('3.2 Scalabilité'),
  p("Les instances applicatives sont sans état (session côté Redis, pas en mémoire locale), ce qui permet d'en ajouter horizontalement derrière le load balancer selon la charge observée, sans changement de code."),
  pageBreak(),

  h1('4. Modèle de données complet'),
  h2('4.1 ERD textuel'),
  ...codeBlock(erd),
  h2('4.2 Schéma PostgreSQL — Auth / Users'),
  ...codeBlock(ddlAuth),
  h2('4.3 Schéma PostgreSQL — Organizations'),
  ...codeBlock(ddlOrgs),
  h2('4.4 Schéma PostgreSQL — Campaigns / Tasks'),
  ...codeBlock(ddlCampaigns),
  h2('4.5 Schéma PostgreSQL — Participations / Submissions / Validation'),
  ...codeBlock(ddlParticipations),
  h2('4.6 Schéma PostgreSQL — Wallet / Ledger (cœur financier)'),
  ...codeBlock(ddlWallet),
  h2('4.7 Schéma PostgreSQL — Payments / Withdrawals'),
  ...codeBlock(ddlPayments),
  h2('4.8 Schéma PostgreSQL — Disputes / Notifications / Fraud / Audit'),
  ...codeBlock(ddlOther),
  pageBreak(),

  h1('5. Sécurité'),
  dataTable({
    header: ['Thème', 'Mesure MVP'],
    widths: [2400, 7238],
    rows: [
      ['Authentification', 'Email+mot de passe (hash Argon2id), JWT access (15 min) + refresh token rotatif stocké en Redis (révocable).'],
      ['Vérification identité', 'Email (lien) + téléphone (OTP SMS) obligatoires avant toute participation/retrait.'],
      ['MFA', 'Optionnel pour USER, obligatoire pour PLATFORM_ADMIN/OPERATOR et ORGANIZATION_OWNER (TOTP).'],
      ['Autorisation', "RBAC applicatif (Spring Security) + vérification de propriété de ressource."],
      ['Sessions', 'Refresh tokens révocables individuellement, limite de sessions actives simultanées configurable.'],
      ['Rate limiting', 'Redis (sliding window) sur login, OTP, participation, retrait.'],
      ['Anti-bot', 'CAPTCHA sur inscription et login après N échecs ; honeypot sur formulaires critiques.'],
      ['Comptes multiples', 'Device fingerprint + corrélation téléphone/IP à l\'inscription.'],
      ['Contrôle des retraits', 'Seuils avec revue manuelle au-delà d\'un montant ; KYC documentaire au-delà d\'un cumul.'],
      ['Idempotence financière', 'Header Idempotency-Key obligatoire sur les opérations financières et les webhooks provider.'],
      ['Audit', 'Toute action sensible journalisée en AuditLog, jamais modifiable.'],
      ['Transport', 'HTTPS obligatoire, HSTS, cookies Secure/HttpOnly.'],
      ['Secrets', 'Clés API provider en coffre-fort (Vault/secrets manager), jamais en base en clair.'],
      ['Protection OWASP', 'Validation stricte des entrées, requêtes paramétrées (JPA), CSP, protection CSRF.'],
    ],
  }),
  h2('5.1 Prévention des doublons critiques'),
  dataTable({
    header: ['Risque', 'Mécanisme'],
    widths: [2800, 6838],
    rows: [
      ['Double paiement', "idempotency_key unique sur payments + vérification de la référence provider avant traitement du webhook."],
      ['Double récompense', "rewards.participation_id UNIQUE + transition d'état stricte (seule APPROVED déclenche le crédit, une seule fois)."],
      ['Double soumission', "Index unique partiel sur participation active (BR-020)."],
      ['Double retrait', "idempotency_key unique sur withdrawals + débit immédiat vers reserved_balance dès la demande."],
      ['Manipulation du solde', "Le solde n'est jamais modifié directement — uniquement via l'insertion d'une wallet_transaction dans une transaction SQL qui recalcule le solde."],
      ['Fraude comptes multiples', "Voir §7 — Anti-fraude du dossier de conception produit."],
      ['Automatisation abusive', "Rate limiting + détection de patterns temporels anormaux + CAPTCHA sur soumissions à fort enjeu."],
    ],
  }),
  pageBreak(),

  h1('6. Concurrence et intégrité financière'),
  p("Cas de référence : une tâche a 10 places restantes, 100 utilisateurs tentent de participer simultanément. Le système doit garantir qu'il n'y aura pas plus de 10 participants. La même logique s'applique au budget, au solde et aux retraits."),
  h2('6.1 Mise à jour conditionnelle atomique (CAS)'),
  p("Mécanisme retenu : mise à jour conditionnelle atomique plutôt qu'un verrou pessimiste long :"),
  ...codeBlock(`UPDATE tasks
SET current_participants = current_participants + 1, version = version + 1
WHERE id = :taskId
  AND current_participants < max_participants
RETURNING id;`),
  p("Si RETURNING ne renvoie aucune ligne, la place n'était plus disponible (409 TASK_FULL immédiat, pas de retry silencieux). Si une ligne est retournée, l'insertion de la participation se fait dans la même transaction."),
  p("Avantage sur un verrou pessimiste (SELECT ... FOR UPDATE) tenu pendant tout le traitement métier : la fenêtre de verrouillage réelle est celle d'un seul UPDATE, donc très courte — bien plus adapté à un pic de 100 requêtes concurrentes."),
  p("Pour le budget de campagne, même logique sur campaigns.budget_reserved / budget_consumed via UPDATE ... WHERE budget_consumed + :amount <= budget_reserved. Pour le wallet (retrait, débit) : UPDATE wallets SET available_balance = available_balance - :amount WHERE available_balance >= :amount — garantit available_balance ≥ 0 (BR-041) sans jamais lire-puis-écrire de façon non atomique."),
  h2('6.2 Optimistic vs pessimistic locking'),
  bullet("Optimistic locking (colonne version) : mécanisme par défaut partout où les conflits sont rares comparés au volume de lecture (campagnes, tâches) — fonctionne bien combiné au CAS."),
  bullet("Pessimistic locking (SELECT ... FOR UPDATE) : réservé aux rares opérations multi-lignes qui doivent voir un état cohérent avant d'écrire (ex. clôture de campagne : lire toutes les participations en cours + le wallet organisation pour calculer le remboursement du reliquat)."),
  bullet("Idempotency key : pour toute opération déclenchée par une action externe potentiellement rejouée (webhook, retry réseau), contrainte UNIQUE en base — un rejeu renvoie la réponse déjà produite plutôt que de rejouer l'effet de bord financier."),
  bullet("Contraintes SQL (CHECK, UNIQUE, index partiels) : dernière ligne de défense si un bug applicatif contourne la logique service."),
  pageBreak(),

  h1('7. Sauvegarde et plan de reprise'),
  dataTable({
    header: ['Élément', 'Politique'],
    widths: [2800, 6838],
    rows: [
      ['Sauvegarde PostgreSQL', 'Sauvegardes automatisées quotidiennes, rétention 30 jours.'],
      ['Réplication', "Réplique PostgreSQL en lecture seule, promouvable en cas de bascule."],
      ['Stockage objet', "Réplication native du service S3-compatible utilisé (selon le fournisseur retenu)."],
      ['Objectif de reprise (RPO)', "Inférieur à 24h en MVP (une seule région, pas de réplication multi-région à ce stade)."],
      ['Objectif de continuité (RTO)', "Bascule manuelle documentée sur la réplique en cas d'incident sur le primaire, en attendant l'automatisation en V2."],
    ],
  }),
  pageBreak(),

  h1('8. Observabilité'),
  bullet("Logs structurés (JSON) avec correlationId (propagé depuis l'en-tête X-Request-Id ou généré), actorId, action."),
  bullet("Métriques (Micrometer → Prometheus) : latence par endpoint, taux d'erreur, volume de transactions financières par minute, taille des files de modération/retrait/litige."),
  bullet("Health checks (/actuator/health) incluant l'état de la base de données et de Redis."),
  bullet("L'audit (AuditLog) complète les logs techniques par une trace métier lisible humainement : qui a fait quoi, quand, sur quelle ressource, avec quel résultat."),
  pageBreak(),

  h1('9. Stratégie de tests'),
  dataTable({
    header: ['Type', 'Objectif'],
    widths: [2400, 7238],
    rows: [
      ['Unitaires', "Logique métier pure (règles de transition d'état, calcul de commission)."],
      ['Intégration', "Interaction service ↔ base réelle."],
      ['API', "Contrats des endpoints, codes d'erreur."],
      ['Sécurité', "RBAC, injection, rate limiting."],
      ['Concurrence', "100 requêtes simultanées sur une tâche à 10 places → exactement 10 task_participations créées ; idem sur budget de campagne et retrait rejoué avec la même idempotency key."],
      ['Financiers', "SUM(wallet_transactions.amount) = wallets.available_balance + reserved_balance après tout scénario (y compris annulation, remboursement)."],
      ['End-to-end', "Inscription → participation → soumission → validation → crédit → retrait simulé."],
    ],
  }),
  note("Cas de test explicitement couverts : double participation, double récompense, double retrait, campagne sans budget suffisant, concurrence sur les dernières places, remboursement, annulation, rejet puis litige."),
  pageBreak(),

  h1('10. Risques techniques'),
  dataTable({
    header: ['Risque', 'Mitigation'],
    widths: [4200, 5038],
    rows: [
      ['Incohérence du solde wallet sous forte concurrence', 'Transactions SQL + mise à jour conditionnelle + tests de concurrence dédiés.'],
      ['Dépendance à un seul provider de paiement non livré à temps', "Abstraction PaymentProvider + mode simulé pour ne pas bloquer le développement."],
      ['Volume de modération manuelle dépassant la capacité de l\'équipe', 'SLA configurables + escalade automatique (BR-032) + priorisation.'],
      ['Statut légal des paiements intermédiés non clarifié', 'Validation juridique locale avant un lancement public à grande échelle.'],
    ],
  }),
  pageBreak(),

  h1('11. Roadmap de développement'),
  dataTable({
    header: ['Phase', 'Objectif', 'Critère de validation'],
    widths: [900, 4800, 3938],
    rows: [
      ['1', 'Architecture et base technique', "Un endpoint /health déployé automatiquement."],
      ['2', 'Authentification et utilisateurs', "Un USER peut s'inscrire et se connecter en toute sécurité."],
      ['3', 'Organisations', 'Un OWNER peut inviter un MANAGER.'],
      ['4', 'Campagnes et tâches', 'Une campagne peut être créée et passer en PENDING_REVIEW.'],
      ['5', 'Participations et soumissions', 'BR-020/021/022 vérifiées par tests de concurrence.'],
      ['6', 'Validation', 'Un rejet avec motif déclenche un statut cohérent.'],
      ['7', 'Wallet et ledger', 'Invariant solde = somme ledger vérifié par test.'],
      ['8', 'Paiements et retraits', 'Un retrait simulé complète le cycle REQUESTED→COMPLETED.'],
      ['9', 'Administration (back-office)', 'Un admin peut traiter chaque file de bout en bout.'],
      ['10', 'Anti-fraude', 'Une soumission dupliquée génère un événement.'],
      ['11', 'Statistiques', 'Les KPI sont affichés et corrects sur données de test.'],
    ],
  }),
];

H.buildAndSave(sections, '../06-DAT-Dossier-Architecture-Technique.docx');
