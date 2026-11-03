package com.platform.participations.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** BR-021 — releases slots held by reservations nobody submitted in time. */
@Component
public class ReservationExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReservationExpiryScheduler.class);

    private final ParticipationService participationService;

    public ReservationExpiryScheduler(ParticipationService participationService) {
        this.participationService = participationService;
    }

    @Scheduled(fixedDelayString = "${participations.reservation-expiry-check-interval-ms:60000}")
    public void expireOverdueReservations() {
        int expired = participationService.expireOverdueReservations();
        if (expired > 0) {
            log.info("Expired {} overdue reservation(s)", expired);
        }
    }
}
