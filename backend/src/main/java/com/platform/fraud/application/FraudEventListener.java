package com.platform.fraud.application;

import com.platform.fraud.domain.FraudEventType;
import com.platform.fraud.domain.FraudRiskLevel;
import com.platform.participations.domain.event.DuplicateSubmissionDetectedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * This phase's one detector (BR-051). {@code participations} publishes, {@code fraud} listens —
 * same direction as {@code AuditLogListener} (Phase 9). Best-effort like that listener too: a
 * failure here must never roll back the submission itself, which is the actual user-facing action.
 */
@Component
public class FraudEventListener {

    private static final Logger log = LoggerFactory.getLogger(FraudEventListener.class);

    private final FraudService fraudService;

    public FraudEventListener(FraudService fraudService) {
        this.fraudService = fraudService;
    }

    @EventListener
    public void onDuplicateSubmissionDetected(DuplicateSubmissionDetectedEvent event) {
        try {
            fraudService.record(FraudEventType.DUPLICATE_SUBMISSION, FraudRiskLevel.MEDIUM, event.userId(),
                    event.campaignId(), "Soumission " + event.submissionId() + " identique (hash de contenu) à la "
                            + "soumission " + event.duplicateOfSubmissionId() + " sur la tâche " + event.taskId() + " (BR-051).");
        } catch (RuntimeException e) {
            log.warn("Failed to record fraud event for duplicate submission {}", event.submissionId(), e);
        }
    }
}
