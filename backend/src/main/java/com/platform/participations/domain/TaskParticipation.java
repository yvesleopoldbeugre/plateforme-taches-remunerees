package com.platform.participations.domain;

import com.platform.common.exception.BusinessException;
import com.platform.common.exception.ForbiddenException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Maps to {@code task_participations} (DAT §4.2). BR-020 (one active participation per user per
 * task) is enforced by the DB's partial unique index {@code uq_active_participation}, not here —
 * this class only enforces the status transitions.
 */
@Entity
@Table(name = "task_participations")
public class TaskParticipation {

    private static final Set<ParticipationStatus> SUBMITTABLE =
            EnumSet.of(ParticipationStatus.RESERVED, ParticipationStatus.CORRECTION_REQUESTED);

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ParticipationStatus status = ParticipationStatus.RESERVED;

    @Column(name = "reserved_at", nullable = false)
    private Instant reservedAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected TaskParticipation() {
        // JPA
    }

    public TaskParticipation(UUID taskId, UUID userId, Instant expiresAt) {
        this.taskId = taskId;
        this.userId = userId;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired() {
        return status == ParticipationStatus.RESERVED && Instant.now().isAfter(expiresAt);
    }

    public void assertOwnedBy(UUID requesterId) {
        if (!userId.equals(requesterId)) {
            throw new ForbiddenException("NOT_YOUR_PARTICIPATION", "Cette participation ne vous appartient pas.");
        }
    }

    public void submit() {
        if (!SUBMITTABLE.contains(status)) {
            throw new BusinessException("PARTICIPATION_NOT_SUBMITTABLE", HttpStatus.CONFLICT,
                    "Cette participation ne peut pas recevoir de soumission (statut actuel : " + status + ").");
        }
        this.status = ParticipationStatus.UNDER_REVIEW;
        this.submittedAt = Instant.now();
    }

    public void expire() {
        this.status = ParticipationStatus.EXPIRED;
        this.resolvedAt = Instant.now();
    }

    public void cancel() {
        if (status != ParticipationStatus.RESERVED && status != ParticipationStatus.IN_PROGRESS) {
            throw new BusinessException("PARTICIPATION_NOT_CANCELLABLE", HttpStatus.CONFLICT,
                    "Seule une participation réservée peut être annulée (statut actuel : " + status + ").");
        }
        this.status = ParticipationStatus.CANCELLED;
        this.resolvedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public UUID getUserId() {
        return userId;
    }

    public ParticipationStatus getStatus() {
        return status;
    }

    public Instant getReservedAt() {
        return reservedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }
}
