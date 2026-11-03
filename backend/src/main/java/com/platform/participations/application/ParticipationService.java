package com.platform.participations.application;

import com.platform.campaigns.application.CampaignService;
import com.platform.campaigns.domain.Campaign;
import com.platform.campaigns.domain.CampaignStatus;
import com.platform.common.exception.BusinessException;
import com.platform.common.exception.ConflictException;
import com.platform.common.exception.NotFoundException;
import com.platform.participations.domain.ParticipationStatus;
import com.platform.participations.domain.TaskParticipation;
import com.platform.participations.infrastructure.TaskParticipationRepository;
import com.platform.common.exception.ForbiddenException;
import com.platform.submissions.application.SubmissionService;
import com.platform.tasks.application.TaskService;
import com.platform.tasks.domain.Task;
import com.platform.tasks.domain.TaskStatus;
import com.platform.users.application.UserService;
import com.platform.users.domain.User;
import com.platform.users.domain.UserStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for participations (UC-09, UC-10 orchestration). This is where BR-020,
 * BR-021 and BR-022 actually meet — see DAT §6.1/§9 for the concurrency rationale and
 * {@code ParticipationConcurrencyTest} for the tests the roadmap's Phase 5 milestone asks for.
 */
@Service
public class ParticipationService {

    private final TaskParticipationRepository participationRepository;
    private final TaskService taskService;
    private final CampaignService campaignService;
    private final SubmissionService submissionService;
    private final UserService userService;

    public ParticipationService(TaskParticipationRepository participationRepository, TaskService taskService,
                                 CampaignService campaignService, SubmissionService submissionService,
                                 UserService userService) {
        this.participationRepository = participationRepository;
        this.taskService = taskService;
        this.campaignService = campaignService;
        this.submissionService = submissionService;
        this.userService = userService;
    }

    @Transactional
    public TaskParticipation participate(UUID taskId, UUID userId) {
        assertUserCanParticipate(userId);
        Task task = taskService.getTask(taskId);
        if (task.getStatus() != TaskStatus.ACTIVE) {
            throw new ConflictException("TASK_NOT_ACTIVE", "Cette tâche n'accepte plus de nouvelles participations.");
        }
        Campaign campaign = campaignService.getCampaign(task.getCampaignId());
        if (campaign.getStatus() != CampaignStatus.PUBLISHED) {
            throw new ConflictException("CAMPAIGN_NOT_PUBLISHED", "Cette campagne n'est pas (ou plus) publiée.");
        }

        // BR-022 — atomic CAS, not read-then-write (DAT §6.1).
        if (!taskService.tryReserveSlot(taskId)) {
            throw new ConflictException("TASK_FULL", "Cette tâche n'a plus de place disponible.");
        }

        Instant expiresAt = Instant.now().plus(task.getReservationTtlMinutes(), ChronoUnit.MINUTES);
        try {
            // flush now: the BR-020 unique-index violation (if any) must surface inside this
            // try block, not later at end-of-transaction — see uq_active_participation (V1).
            return participationRepository.saveAndFlush(new TaskParticipation(taskId, userId, expiresAt));
        } catch (DataIntegrityViolationException e) {
            // The whole @Transactional method rolls back on this unchecked exception, which
            // also undoes the slot reservation above — no manual release needed (DAT §25).
            throw new ConflictException("ALREADY_PARTICIPATING", "Vous participez déjà activement à cette tâche (BR-020).");
        }
    }

    @Transactional
    public TaskParticipation submit(UUID participationId, UUID userId, Map<String, Object> content, Object attachments) {
        TaskParticipation participation = getParticipation(participationId);
        participation.assertOwnedBy(userId);

        if (participation.isExpired()) {
            participation.expire();
            taskService.releaseSlot(participation.getTaskId());
            throw new BusinessException("PARTICIPATION_EXPIRED", HttpStatus.GONE,
                    "Le délai de réservation est dépassé (BR-021).");
        }

        // May throw DUPLICATE_SUBMISSION_HASH (BR-051) — participation stays as-is, resubmission
        // with different content remains possible.
        submissionService.recordSubmission(participationId, participation.getTaskId(), content, attachments);
        participation.submit();
        return participation;
    }

    @Transactional
    public void cancel(UUID participationId, UUID userId) {
        TaskParticipation participation = getParticipation(participationId);
        participation.assertOwnedBy(userId);
        participation.cancel();
        taskService.releaseSlot(participation.getTaskId());
    }

    /** BR-001 (email+téléphone vérifiés) and BR-002 (compte suspendu/banni) gate participation. */
    private void assertUserCanParticipate(UUID userId) {
        User user = userService.getById(userId);
        if (user.getStatus() == UserStatus.SUSPENDED || user.getStatus() == UserStatus.BANNED) {
            throw new ForbiddenException("ACCOUNT_SUSPENDED", "Ce compte ne peut pas participer à une tâche.");
        }
        if (!user.isEmailVerified() || !user.isPhoneVerified()) {
            throw new ForbiddenException("ACCOUNT_NOT_VERIFIED",
                    "L'email et le téléphone doivent être vérifiés avant de participer (BR-001).");
        }
    }

    public TaskParticipation getParticipation(UUID participationId) {
        return participationRepository.findById(participationId)
                .orElseThrow(() -> new NotFoundException("PARTICIPATION_NOT_FOUND", "Participation introuvable."));
    }

    public List<TaskParticipation> getMyParticipations(UUID userId) {
        return participationRepository.findByUserId(userId);
    }

    public List<TaskParticipation> listByTask(UUID taskId) {
        return participationRepository.findByTaskId(taskId);
    }

    /** Called by {@link ReservationExpiryScheduler} — BR-021. */
    @Transactional
    public int expireOverdueReservations() {
        List<TaskParticipation> overdue =
                participationRepository.findByStatusAndExpiresAtBefore(ParticipationStatus.RESERVED, Instant.now());
        for (TaskParticipation participation : overdue) {
            participation.expire();
            taskService.releaseSlot(participation.getTaskId());
        }
        return overdue.size();
    }
}
