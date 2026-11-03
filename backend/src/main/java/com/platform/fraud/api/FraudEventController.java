package com.platform.fraud.api;

import com.platform.fraud.api.dto.FraudEventDecisionRequest;
import com.platform.fraud.api.dto.FraudEventResponse;
import com.platform.fraud.application.FraudService;
import com.platform.fraud.domain.FraudEventStatus;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * UC-22. {@code review}/{@code dismiss} match the CDCF's "revue de fraude de premier niveau" for
 * `PLATFORM_OPERATOR`; {@code confirm} triggers a user suspension (UC-21), so it's ADMIN-only —
 * same split as {@code UserController#suspend} (Phase 9).
 */
@RestController
@RequestMapping("/api/v1/fraud-events")
@PreAuthorize("hasAnyAuthority('ROLE_PLATFORM_ADMIN', 'ROLE_PLATFORM_OPERATOR')")
public class FraudEventController {

    private final FraudService fraudService;

    public FraudEventController(FraudService fraudService) {
        this.fraudService = fraudService;
    }

    @GetMapping
    public List<FraudEventResponse> queue(@RequestParam FraudEventStatus status) {
        return fraudService.listByStatus(status).stream().map(FraudEventResponse::of).toList();
    }

    @PostMapping("/{fraudEventId}/review")
    public FraudEventResponse review(Authentication authentication, @PathVariable UUID fraudEventId,
                                      @Valid @RequestBody FraudEventDecisionRequest request) {
        return FraudEventResponse.of(fraudService.markReviewed(currentUserId(authentication), fraudEventId, request.note()));
    }

    @PostMapping("/{fraudEventId}/dismiss")
    public FraudEventResponse dismiss(Authentication authentication, @PathVariable UUID fraudEventId,
                                       @Valid @RequestBody FraudEventDecisionRequest request) {
        return FraudEventResponse.of(fraudService.dismiss(currentUserId(authentication), fraudEventId, request.note()));
    }

    @PostMapping("/{fraudEventId}/confirm")
    @PreAuthorize("hasAuthority('ROLE_PLATFORM_ADMIN')")
    public FraudEventResponse confirm(Authentication authentication, @PathVariable UUID fraudEventId,
                                       @Valid @RequestBody FraudEventDecisionRequest request) {
        return FraudEventResponse.of(fraudService.confirm(currentUserId(authentication), fraudEventId, request.note()));
    }

    private UUID currentUserId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
