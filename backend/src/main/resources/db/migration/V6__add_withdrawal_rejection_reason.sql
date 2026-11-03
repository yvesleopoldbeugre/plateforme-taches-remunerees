-- Same gap as V4 for campaigns: a user whose withdrawal is rejected should know why, but V1
-- never gave withdrawals a reason column. Needed now that Phase 8 builds the operator-facing
-- approve/reject flow.
ALTER TABLE withdrawals
    ADD COLUMN rejection_reason TEXT;
