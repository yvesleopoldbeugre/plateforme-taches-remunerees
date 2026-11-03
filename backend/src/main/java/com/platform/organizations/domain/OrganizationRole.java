package com.platform.organizations.domain;

/** Mirrors the organization_members.role CHECK constraint (DAT §4.2). Scoped to one org. */
public enum OrganizationRole {
    OWNER,
    MANAGER
}
