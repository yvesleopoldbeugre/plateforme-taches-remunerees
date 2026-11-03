package com.platform.organizations.domain;

/** Mirrors the organization_members.status CHECK constraint (DAT §4.2). */
public enum MemberStatus {
    INVITED,
    ACTIVE,
    REMOVED
}
