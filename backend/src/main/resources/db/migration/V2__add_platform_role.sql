-- The DAT defines PLATFORM_ADMIN / PLATFORM_OPERATOR as platform-wide roles, distinct from the
-- per-organization roles already carried by organization_members.role. V1 did not give them a
-- home on the users table; this fixes that gap now that the auth/RBAC module is being built.
ALTER TABLE users
    ADD COLUMN platform_role VARCHAR(32),
    ADD CONSTRAINT chk_users_platform_role
        CHECK (platform_role IS NULL OR platform_role IN ('PLATFORM_ADMIN', 'PLATFORM_OPERATOR'));
