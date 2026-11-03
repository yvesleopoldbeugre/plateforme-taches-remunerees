-- Same gap as V4 (campaigns) and V6 (withdrawals): an organization whose verification is
-- rejected should know why. Needed now that Phase 9 builds the admin verification flow.
ALTER TABLE organizations
    ADD COLUMN kyc_rejection_reason TEXT;
