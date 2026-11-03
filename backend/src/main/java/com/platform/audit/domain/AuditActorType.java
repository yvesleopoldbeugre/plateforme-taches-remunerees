package com.platform.audit.domain;

/** Mirrors {@code audit_logs.actor_type} (DAT §4.2). Every admin action in this MVP has a human
 * actor; {@code SYSTEM} is reserved for future automated actions (e.g. fraud auto-suspension, Phase 10). */
public enum AuditActorType {
    USER,
    SYSTEM
}
