-- Every other status-like column got a CHECK constraint in V1 except kyc_status — closing that
-- gap now that the organizations module (which owns it) is being built.
ALTER TABLE organizations
    ADD CONSTRAINT chk_org_kyc_status
        CHECK (kyc_status IN ('NOT_SUBMITTED', 'PENDING_REVIEW', 'VERIFIED', 'REJECTED'));
