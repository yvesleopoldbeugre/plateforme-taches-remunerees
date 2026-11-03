package com.platform.validation.application;

import com.platform.participations.domain.event.ParticipationSubmittedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Reacts to every submission to attempt auto-validation (UC-11). Synchronous, same transaction
 * as the submission — but a failure here must never take the submission down with it, so
 * everything is caught: the worst case is simply "falls back to manual review".
 */
@Component
public class AutoValidationListener {

    private static final Logger log = LoggerFactory.getLogger(AutoValidationListener.class);

    private final ValidationService validationService;

    public AutoValidationListener(ValidationService validationService) {
        this.validationService = validationService;
    }

    @EventListener
    public void onSubmissionRecorded(ParticipationSubmittedEvent event) {
        try {
            validationService.attemptAutoValidation(
                    event.submissionId(), event.participationId(), event.taskId(), event.content());
        } catch (RuntimeException e) {
            log.warn("Auto-validation attempt failed for submission {} — falling back to manual review",
                    event.submissionId(), e);
        }
    }
}
