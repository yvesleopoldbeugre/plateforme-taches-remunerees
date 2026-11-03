package com.platform.campaigns.api.dto;

import com.platform.campaigns.domain.CampaignEligibilityCriteria;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record EligibilityCriteriaResponse(
        UUID campaignId, Short minAge, Short maxAge, String gender, String genderJustification,
        List<String> countries, List<String> languages, BigDecimal minReputation
) {
    public static EligibilityCriteriaResponse of(CampaignEligibilityCriteria c) {
        return new EligibilityCriteriaResponse(c.getCampaignId(), c.getMinAge(), c.getMaxAge(), c.getGender(),
                c.getGenderJustification(), c.getCountries(), c.getLanguages(), c.getMinReputation());
    }
}
