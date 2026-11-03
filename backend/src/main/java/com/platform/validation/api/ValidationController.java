package com.platform.validation.api;

import com.platform.validation.api.dto.DecideValidationRequest;
import com.platform.validation.api.dto.ValidationResponse;
import com.platform.validation.application.ValidationService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ValidationController {

    private final ValidationService validationService;

    public ValidationController(ValidationService validationService) {
        this.validationService = validationService;
    }

    @PostMapping("/api/v1/validations/{submissionId}")
    public ValidationResponse decide(Authentication authentication, @PathVariable UUID submissionId,
                                      @Valid @RequestBody DecideValidationRequest request) {
        UUID reviewerId = UUID.fromString(authentication.getName());
        return ValidationResponse.of(
                validationService.decide(submissionId, reviewerId, request.decision(), request.reasonCode(), request.comment()));
    }
}
