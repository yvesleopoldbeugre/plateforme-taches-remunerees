-- Initial schema — Plateforme de Tâches & Campagnes Rémunérées (MVP)
-- Mirrors the DDL in the Dossier d'Architecture Technique (DAT), §4.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- =========================================================
-- AUTH / USERS
-- =========================================================
CREATE TABLE users (
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
);

-- =========================================================
-- ORGANIZATIONS
-- =========================================================
CREATE TABLE organizations (
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
);

-- =========================================================
-- CAMPAIGNS / TASKS
-- =========================================================
CREATE TABLE campaigns (
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
CREATE INDEX idx_tasks_type_status ON tasks(type, status);

-- =========================================================
-- PARTICIPATIONS / SUBMISSIONS / VALIDATION
-- =========================================================
CREATE TABLE task_participations (
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
);

-- =========================================================
-- WALLET / LEDGER (cœur financier)
-- =========================================================
CREATE TABLE wallets (
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
    -- INSERT-ONLY: no application-level UPDATE/DELETE (enforced via DB role + revoke)
);
CREATE INDEX idx_wallet_tx_wallet ON wallet_transactions(wallet_id, created_at);
CREATE INDEX idx_wallet_tx_reference ON wallet_transactions(reference_type, reference_id);

-- =========================================================
-- PAYMENTS / WITHDRAWALS
-- =========================================================
CREATE TABLE payment_methods (
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
CREATE INDEX idx_withdrawals_status ON withdrawals(status, requested_at);

-- =========================================================
-- DISPUTES / NOTIFICATIONS / FRAUD / AUDIT
-- =========================================================
CREATE TABLE disputes (
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
CREATE INDEX idx_audit_actor ON audit_logs(actor_type, actor_id, created_at);
