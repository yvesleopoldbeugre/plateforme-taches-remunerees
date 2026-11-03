package com.platform.statistics.api;

import com.platform.statistics.api.dto.UserStatisticsResponse;
import com.platform.statistics.application.UserStatisticsService;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** UC-20 — self only. */
@RestController
public class UserStatisticsController {

    private final UserStatisticsService statisticsService;

    public UserStatisticsController(UserStatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/api/v1/users/me/statistics")
    public UserStatisticsResponse me(Authentication authentication) {
        return statisticsService.compute(UUID.fromString(authentication.getName()));
    }
}
