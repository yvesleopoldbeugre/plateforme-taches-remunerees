package com.platform.statistics.api;

import com.platform.statistics.api.dto.UserStatisticsResponse;
import com.platform.statistics.application.UserStatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** UC-20 — self only. */
@RestController
@Tag(name = "Statistiques", description = "Indicateurs clés pour une organisation, un utilisateur, ou "
        + "la plateforme (CDCF §15) — lecture pure, calculée à la demande, aucune table propre.")
public class UserStatisticsController {

    private final UserStatisticsService statisticsService;

    public UserStatisticsController(UserStatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @Operation(summary = "Mes KPI", description = "Tâches réalisées/validées/rejetées, taux de réussite, "
            + "revenu total et moyen, montant et fréquence des retraits.")
    @GetMapping("/api/v1/users/me/statistics")
    public UserStatisticsResponse me(Authentication authentication) {
        return statisticsService.compute(UUID.fromString(authentication.getName()));
    }
}
