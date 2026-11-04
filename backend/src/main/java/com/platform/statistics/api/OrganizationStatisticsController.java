package com.platform.statistics.api;

import com.platform.statistics.api.dto.OrganizationStatisticsResponse;
import com.platform.statistics.application.OrganizationStatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** UC-19 — any active member can view, not just OWNER/MANAGER (same reasoning as the member list). */
@RestController
@Tag(name = "Statistiques", description = "Indicateurs clés pour une organisation, un utilisateur, ou "
        + "la plateforme (CDCF §15) — lecture pure, calculée à la demande, aucune table propre.")
public class OrganizationStatisticsController {

    private final OrganizationStatisticsService statisticsService;

    public OrganizationStatisticsController(OrganizationStatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @Operation(summary = "KPI d'une organisation", description = "Campagnes créées/actives, "
            + "participations, taux de validation/rejet/abandon, budget dépensé, coût moyen par "
            + "participation. Réservé aux membres actifs.")
    @GetMapping("/api/v1/organizations/{organizationId}/statistics")
    public OrganizationStatisticsResponse get(Authentication authentication, @PathVariable UUID organizationId) {
        return statisticsService.compute(organizationId, UUID.fromString(authentication.getName()));
    }
}
