package com.platform.audit.application;

import com.platform.audit.domain.AuditActorType;
import com.platform.audit.domain.AuditLog;
import com.platform.audit.infrastructure.AuditLogRepository;
import com.platform.common.exception.BusinessException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application service for {@link AuditLog} — a dependency-free noyau like {@code wallet}: it
 * only ever writes what a {@code DomainEvent} listener ({@code AuditLogListener}) hands it. */
@Service
public class AuditService {

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public AuditLog record(UUID actorId, String action, String resourceType, UUID resourceId,
                            String beforeState, String afterState) {
        return repository.save(new AuditLog(AuditActorType.USER, actorId, action, resourceType, resourceId,
                beforeState, afterState));
    }

    /** {@code resourceType} is required — listing the entire table is not a feature this MVP exposes. */
    public List<AuditLog> search(String resourceType, UUID resourceId) {
        if (resourceType == null || resourceType.isBlank()) {
            throw new BusinessException("RESOURCE_TYPE_REQUIRED", HttpStatus.BAD_REQUEST,
                    "Le paramètre resourceType est requis.");
        }
        return resourceId != null
                ? repository.findByResourceTypeAndResourceIdOrderByCreatedAtDesc(resourceType, resourceId)
                : repository.findByResourceTypeOrderByCreatedAtDesc(resourceType);
    }
}
