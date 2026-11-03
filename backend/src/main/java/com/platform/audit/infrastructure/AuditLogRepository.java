package com.platform.audit.infrastructure;

import com.platform.audit.domain.AuditLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findByResourceTypeOrderByCreatedAtDesc(String resourceType);

    List<AuditLog> findByResourceTypeAndResourceIdOrderByCreatedAtDesc(String resourceType, UUID resourceId);
}
