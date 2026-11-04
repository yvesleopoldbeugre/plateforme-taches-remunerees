package com.platform.audit.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.organizations.domain.event.OrganizationStatusChangedEvent;
import com.platform.users.domain.event.UserSecurityActionEvent;
import com.platform.users.domain.event.UserStatusChangedEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * The "transverse passif, abonné aux événements de domaine" half of the {@code audit}
 * module's own package-info: {@code users} and {@code organizations} publish, this just listens
 * and writes an immutable row. Like {@code AutoValidationListener} (Phase 6), failures here are
 * caught and logged, never allowed to roll back the admin action itself — losing an audit entry
 * is far better than refusing to suspend a fraudulent account because JSON serialization hiccuped.
 * A stricter "audit must succeed" mode is a reasonable hardening left for a later pass.
 */
@Component
public class AuditLogListener {

    private static final Logger log = LoggerFactory.getLogger(AuditLogListener.class);

    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public AuditLogListener(AuditService auditService, ObjectMapper objectMapper) {
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @EventListener
    public void onUserStatusChanged(UserStatusChangedEvent event) {
        try {
            Map<String, Object> before = new LinkedHashMap<>();
            before.put("status", event.previousStatus().name());
            Map<String, Object> after = new LinkedHashMap<>();
            after.put("status", event.newStatus().name());
            auditService.record(event.actorId(), "USER_STATUS_CHANGED", "USER", event.userId(),
                    toJson(before), toJson(after));
        } catch (RuntimeException e) {
            log.warn("Failed to write audit log for user {} status change", event.userId(), e);
        }
    }

    @EventListener
    public void onOrganizationStatusChanged(OrganizationStatusChangedEvent event) {
        try {
            Map<String, Object> before = new LinkedHashMap<>();
            before.put("status", event.previousStatus().name());
            before.put("kycStatus", event.previousKycStatus().name());
            Map<String, Object> after = new LinkedHashMap<>();
            after.put("status", event.newStatus().name());
            after.put("kycStatus", event.newKycStatus().name());
            if (event.reason() != null) {
                after.put("reason", event.reason());
            }
            auditService.record(event.actorId(), "ORGANIZATION_STATUS_CHANGED", "ORGANIZATION", event.organizationId(),
                    toJson(before), toJson(after));
        } catch (RuntimeException e) {
            log.warn("Failed to write audit log for organization {} status change", event.organizationId(), e);
        }
    }

    /** Phase 12 — MFA enabled/disabled, password reset completed (CDCT §8, "toute action sensible"). */
    @EventListener
    public void onUserSecurityAction(UserSecurityActionEvent event) {
        try {
            auditService.record(event.actorId(), event.action(), "USER", event.userId(), null, null);
        } catch (RuntimeException e) {
            log.warn("Failed to write audit log for user {} security action {}", event.userId(), event.action(), e);
        }
    }

    private String toJson(Map<String, Object> state) {
        try {
            return objectMapper.writeValueAsString(state);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
