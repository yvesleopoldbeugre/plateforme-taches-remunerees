package com.platform.validation.api;

import com.platform.validation.api.dto.DecideValidationRequest;
import com.platform.validation.api.dto.ValidationResponse;
import com.platform.validation.application.ValidationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Validation", description = "Décision manuelle (approbation, rejet, demande de correction) "
        + "sur une soumission (UC-12, UC-13, BR-030, BR-031).")
public class ValidationController {

    private final ValidationService validationService;

    public ValidationController(ValidationService validationService) {
        this.validationService = validationService;
    }

    @Operation(summary = "Décider sur une soumission", description = "APPROVED, REJECTED ou "
            + "CORRECTION_REQUESTED — un motif standardisé (BR-031) est requis sauf pour APPROVED. "
            + "Une approbation règle aussi la récompense dans la même transaction (crédit du portefeuille "
            + "utilisateur, consommation du budget réservé de l'organisation). Réservé aux membres actifs "
            + "(OWNER/MANAGER) de l'organisation propriétaire de la tâche.")
    @PostMapping("/api/v1/validations/{submissionId}")
    public ValidationResponse decide(Authentication authentication, @PathVariable UUID submissionId,
                                      @Valid @RequestBody DecideValidationRequest request) {
        UUID reviewerId = UUID.fromString(authentication.getName());
        return ValidationResponse.of(
                validationService.decide(submissionId, reviewerId, request.decision(), request.reasonCode(), request.comment()));
    }
}
