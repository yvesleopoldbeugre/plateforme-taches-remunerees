package com.platform.statistics.api;

import com.platform.statistics.api.dto.PlatformStatisticsResponse;
import com.platform.statistics.application.PlatformStatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Platform-wide KPI — admin back-office aggregate read, same role split as the Phase 9 queues. */
@RestController
@PreAuthorize("hasAnyAuthority('ROLE_PLATFORM_ADMIN', 'ROLE_PLATFORM_OPERATOR')")
@Tag(name = "Statistiques", description = "Indicateurs clés pour une organisation, un utilisateur, ou "
        + "la plateforme (CDCF §15) — lecture pure, calculée à la demande, aucune table propre.")
public class PlatformStatisticsController {

    private final PlatformStatisticsService statisticsService;

    public PlatformStatisticsController(PlatformStatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @Operation(summary = "KPI de la plateforme (admin)", description = "Utilisateurs/organisations actifs, "
            + "volume financier transité, événements de fraude par niveau de risque. PLATFORM_ADMIN ou OPERATOR.")
    @GetMapping("/api/v1/admin/statistics")
    public PlatformStatisticsResponse get() {
        return statisticsService.compute();
    }
}
