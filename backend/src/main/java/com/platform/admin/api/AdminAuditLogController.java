package com.platform.admin.api;

import com.platform.admin.api.dto.AuditLogResponse;
import com.platform.audit.application.AuditService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Reads back what {@code AuditLogListener} wrote — "journalisation immuable" is only useful if
 * an admin can actually see it. */
@RestController
@PreAuthorize("hasAnyAuthority('ROLE_PLATFORM_ADMIN', 'ROLE_PLATFORM_OPERATOR')")
public class AdminAuditLogController {

    private final AuditService auditService;

    public AdminAuditLogController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/api/v1/admin/audit-logs")
    public List<AuditLogResponse> search(@RequestParam String resourceType,
                                          @RequestParam(required = false) UUID resourceId) {
        return auditService.search(resourceType, resourceId).stream().map(AuditLogResponse::of).toList();
    }
}
