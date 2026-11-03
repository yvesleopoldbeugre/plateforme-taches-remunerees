package com.platform.campaigns.infrastructure;

import com.platform.campaigns.domain.Campaign;
import com.platform.campaigns.domain.CampaignStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignRepository extends JpaRepository<Campaign, UUID> {

    List<Campaign> findByOrganizationId(UUID organizationId);

    /** Cross-organization queue for the admin back-office (Phase 9) — {@code findByOrganizationId}
     *  above is scoped to one org, which can't support "every campaign pending review". */
    List<Campaign> findByStatusOrderByCreatedAtAsc(CampaignStatus status);
}
