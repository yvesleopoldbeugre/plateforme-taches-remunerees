-- Phase 12 (sécurité des comptes) — users.mfa_enabled existed since V1 but nothing ever stored
-- the TOTP secret itself. Nullable: a secret only exists between "setup" and "enable"/"disable".
ALTER TABLE users
    ADD COLUMN mfa_secret VARCHAR(64);
