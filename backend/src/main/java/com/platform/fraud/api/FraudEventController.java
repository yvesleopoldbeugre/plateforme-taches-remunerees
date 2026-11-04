package com.platform.fraud.api;

import com.platform.fraud.api.dto.FraudEventDecisionRequest;
import com.platform.fraud.api.dto.FraudEventResponse;
import com.platform.fraud.application.FraudService;
import com.platform.fraud.domain.FraudEventStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Anti-fraude", description = "File des événements de fraude et décisions : revue, classement "
        + "sans suite, confirmation (UC-22, BR-051). Détecteur actuel : soumission dupliquée.")
public class FraudEventController {

    private final FraudService fraudService;

    public FraudEventController(FraudService fraudService) {
        this.fraudService = fraudService;
    }

    @Operation(summary = "File des événements de fraude")
    @GetMapping
    public List<FraudEventResponse> queue(@RequestParam FraudEventStatus status) {
        return fraudService.listByStatus(status).stream().map(FraudEventResponse::of).toList();
    }

    @Operation(summary = "Marquer un événement comme revu", description = "\"Escalade\" dans le vocabulaire "
            + "du CDCF — pas de décision finale. PLATFORM_ADMIN ou OPERATOR.")
    @PostMapping("/{fraudEventId}/review")
    public FraudEventResponse review(Authentication authentication, @PathVariable UUID fraudEventId,
                                      @Valid @RequestBody FraudEventDecisionRequest request) {
        return FraudEventResponse.of(fraudService.markReviewed(currentUserId(authentication), fraudEventId, request.note()));
    }

    @Operation(summary = "Classer un événement sans suite", description = "Faux positif. PLATFORM_ADMIN ou OPERATOR.")
    @PostMapping("/{fraudEventId}/dismiss")
    public FraudEventResponse dismiss(Authentication authentication, @PathVariable UUID fraudEventId,
                                       @Valid @RequestBody FraudEventDecisionRequest request) {
        return FraudEventResponse.of(fraudService.dismiss(currentUserId(authentication), fraudEventId, request.note()));
    }

    @Operation(summary = "Confirmer un événement de fraude (admin)", description = "Suspend immédiatement "
            + "l'utilisateur visé (UC-21) dans le même appel — réservé à PLATFORM_ADMIN seul.")
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
