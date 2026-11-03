package com.platform.organizations.infrastructure;

import com.platform.organizations.domain.Organization;
import com.platform.organizations.domain.OrganizationStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    List<Organization> findByStatusOrderByCreatedAtAsc(OrganizationStatus status);
}
