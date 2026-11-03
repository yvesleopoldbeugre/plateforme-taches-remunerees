package com.platform.campaigns.infrastructure;

import com.platform.campaigns.domain.Campaign;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignRepository extends JpaRepository<Campaign, UUID> {

    List<Campaign> findByOrganizationId(UUID organizationId);
}
