package com.platform.statistics.api;

import com.platform.statistics.api.dto.OrganizationStatisticsResponse;
import com.platform.statistics.application.OrganizationStatisticsService;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** UC-19 — any active member can view, not just OWNER/MANAGER (same reasoning as the member list). */
@RestController
public class OrganizationStatisticsController {

    private final OrganizationStatisticsService statisticsService;

    public OrganizationStatisticsController(OrganizationStatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/api/v1/organizations/{organizationId}/statistics")
    public OrganizationStatisticsResponse get(Authentication authentication, @PathVariable UUID organizationId) {
        return statisticsService.compute(organizationId, UUID.fromString(authentication.getName()));
    }
}
