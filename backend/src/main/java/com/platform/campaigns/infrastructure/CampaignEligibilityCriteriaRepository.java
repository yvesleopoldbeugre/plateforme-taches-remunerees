package com.platform.campaigns.infrastructure;

import com.platform.campaigns.domain.CampaignEligibilityCriteria;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignEligibilityCriteriaRepository extends JpaRepository<CampaignEligibilityCriteria, UUID> {
}
