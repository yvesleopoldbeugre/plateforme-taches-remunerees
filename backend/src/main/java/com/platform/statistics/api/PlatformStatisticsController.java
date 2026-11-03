package com.platform.statistics.api;

import com.platform.statistics.api.dto.PlatformStatisticsResponse;
import com.platform.statistics.application.PlatformStatisticsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Platform-wide KPI — admin back-office aggregate read, same role split as the Phase 9 queues. */
@RestController
@PreAuthorize("hasAnyAuthority('ROLE_PLATFORM_ADMIN', 'ROLE_PLATFORM_OPERATOR')")
public class PlatformStatisticsController {

    private final PlatformStatisticsService statisticsService;

    public PlatformStatisticsController(PlatformStatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/api/v1/admin/statistics")
    public PlatformStatisticsResponse get() {
        return statisticsService.compute();
    }
}
