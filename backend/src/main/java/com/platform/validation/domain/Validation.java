package com.platform.validation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Maps to {@code validations} (DAT §4.2) — one row per decision (BR-030, BR-031). */
@Entity
@Table(name = "validations")
public class Validation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "submission_id", nullable = false)
    private UUID submissionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ValidationDecision decision;

    @Column(name = "reason_code", length = 64)
    private String reasonCode;

    /** Null for AUTO_APPROVED — BR-030 only requires traceability for manual decisions. */
    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column
    private String comment;

    @Column(name = "reviewed_at", nullable = false)
    private Instant reviewedAt = Instant.now();

    protected Validation() {
        // JPA
    }

    public Validation(UUID submissionId, ValidationDecision decision, String reasonCode, UUID reviewedBy, String comment) {
        this.submissionId = submissionId;
        this.decision = decision;
        this.reasonCode = reasonCode;
        this.reviewedBy = reviewedBy;
        this.comment = comment;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSubmissionId() {
        return submissionId;
    }

    public ValidationDecision getDecision() {
        return decision;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public UUID getReviewedBy() {
        return reviewedBy;
    }

    public String getComment() {
        return comment;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }
}
