package com.platform.fraud.application;

import com.platform.common.exception.NotFoundException;
import com.platform.fraud.domain.FraudEvent;
import com.platform.fraud.domain.FraudEventStatus;
import com.platform.fraud.domain.FraudEventType;
import com.platform.fraud.domain.FraudRiskLevel;
import com.platform.fraud.infrastructure.FraudEventRepository;
import com.platform.users.application.UserService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for {@link FraudEvent} (UC-22). Depends on {@code users} one-directionally
 * (to suspend on {@link #confirm}) — {@code fraud} is transverse but, unlike {@code audit}, UC-22
 * names it as an actor that acts, not just logs (DAT §8.1/§8.2).
 */
@Service
public class FraudService {

    private final FraudEventRepository repository;
    private final UserService userService;

    public FraudService(FraudEventRepository repository, UserService userService) {
        this.repository = repository;
        this.userService = userService;
    }

    @Transactional
    public FraudEvent record(FraudEventType type, FraudRiskLevel riskLevel, UUID userId, UUID campaignId, String reason) {
        return repository.save(new FraudEvent(type, riskLevel, userId, campaignId, reason));
    }

    public FraudEvent getById(UUID fraudEventId) {
        return repository.findById(fraudEventId)
                .orElseThrow(() -> new NotFoundException("FRAUD_EVENT_NOT_FOUND", "Événement de fraude introuvable."));
    }

    /** Admin back-office queue (DAT §11 — "chaque file de bout en bout" is this phase's own too). */
    public List<FraudEvent> listByStatus(FraudEventStatus status) {
        return repository.findByStatusOrderByDetectedAtAsc(status);
    }

    public long countByRiskLevel(FraudRiskLevel riskLevel) {
        return repository.countByRiskLevel(riskLevel);
    }

    @Transactional
    public FraudEvent markReviewed(UUID reviewerId, UUID fraudEventId, String note) {
        FraudEvent event = getById(fraudEventId);
        event.markReviewed(reviewerId, note);
        return event;
    }

    @Transactional
    public FraudEvent dismiss(UUID reviewerId, UUID fraudEventId, String note) {
        FraudEvent event = getById(fraudEventId);
        event.dismiss(reviewerId, note);
        return event;
    }

    /**
     * Confirming a fraud event *is* the "suspension de compte" outcome the CDCF describes for
     * UC-22 — not a separate manual step afterwards. Only called when the event actually names a
     * user (campaign-only signals, if any detector ever raises one, have nothing to suspend).
     */
    @Transactional
    public FraudEvent confirm(UUID reviewerId, UUID fraudEventId, String note) {
        FraudEvent event = getById(fraudEventId);
        event.confirm(reviewerId, note);
        if (event.getUserId() != null) {
            userService.suspend(reviewerId, event.getUserId());
        }
        return event;
    }
}
