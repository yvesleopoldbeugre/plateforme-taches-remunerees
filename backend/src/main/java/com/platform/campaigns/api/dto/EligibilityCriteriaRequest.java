package com.platform.campaigns.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record EligibilityCriteriaRequest(
        Short minAge,
        Short maxAge,
        String gender,
        String genderJustification,
        List<String> countries,
        List<String> languages,
        BigDecimal minReputation
) {
}
