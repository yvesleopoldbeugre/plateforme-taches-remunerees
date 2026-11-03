-- The DAT's campaign state diagram shows PENDING_REVIEW -> REJECTED with a reason communicated
-- to the owner, but V1 never gave that reason a column. Needed now that publish/reject (gated
-- to platform roles, ahead of the real BR-010 wallet check that lands in Phase 7) is built.
ALTER TABLE campaigns
    ADD COLUMN rejection_reason TEXT;
