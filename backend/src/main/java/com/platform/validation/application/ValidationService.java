package com.platform.validation.application;

import com.platform.campaigns.application.CampaignService;
import com.platform.campaigns.domain.Campaign;
import com.platform.common.exception.BusinessException;
import com.platform.organizations.application.OrganizationService;
import com.platform.organizations.domain.OrganizationRole;
import com.platform.participations.application.ParticipationService;
import com.platform.participations.domain.TaskParticipation;
import com.platform.submissions.application.SubmissionService;
import com.platform.submissions.domain.TaskSubmission;
import com.platform.tasks.application.TaskService;
import com.platform.tasks.domain.Task;
import com.platform.validation.domain.RejectionReasons;
import com.platform.validation.domain.Validation;
import com.platform.validation.domain.ValidationDecision;
import com.platform.validation.domain.event.SubmissionApprovedEvent;
import com.platform.validation.infrastructure.ValidationRepository;
import com.platform.wallet.application.RewardService;
import com.platform.wallet.application.WalletService;
import com.platform.wallet.domain.ReferenceType;
import com.platform.wallet.domain.Reward;
import com.platform.wallet.domain.RewardStatus;
import com.platform.wallet.domain.WalletOwnerType;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for validation decisions (UC-11 to UC-13). The roadmap's explicit Phase 6
 * milestone — "un rejet avec motif déclenche un statut cohérent" (DAT §11) — is {@link #decide}
 * with {@code REJECTED}; auto-validation ({@link #attemptAutoValidation}) is the UC-11 half.
 *
 * <p>Since Phase 7, an approval also settles the money synchronously, in the same transaction as
 * the decision (see {@link #applyDecision}): the {@link Reward} is created and the user's wallet
 * credited directly here (not via a listener on the {@code wallet} side), so that the whole
 * decision — participation status, campaign {@code budget_consumed}, the reward, the ledger — is
 * one atomic unit. {@code wallet} stays a dependency-free noyau (DAT §8.1): {@code validation}
 * depends on it, never the other way around. {@link SubmissionApprovedEvent} is still published
 * afterwards, purely as information for other, non-critical consumers (e.g. notifications).
 */
@Service
public class ValidationService {

    private final ValidationRepository validationRepository;
    private final SubmissionService submissionService;
    private final ParticipationService participationService;
    private final TaskService taskService;
    private final CampaignService campaignService;
    private final OrganizationService organizationService;
    private final RewardService rewardService;
    private final WalletService walletService;
    private final AutoValidationRuleEvaluator ruleEvaluator;
    private final ApplicationEventPublisher eventPublisher;

    public ValidationService(ValidationRepository validationRepository, SubmissionService submissionService,
                              ParticipationService participationService, TaskService taskService,
                              CampaignService campaignService, OrganizationService organizationService,
                              RewardService rewardService, WalletService walletService,
                              AutoValidationRuleEvaluator ruleEvaluator, ApplicationEventPublisher eventPublisher) {
        this.validationRepository = validationRepository;
        this.submissionService = submissionService;
        this.participationService = participationService;
        this.taskService = taskService;
        this.campaignService = campaignService;
        this.organizationService = organizationService;
        this.rewardService = rewardService;
        this.walletService = walletService;
        this.ruleEvaluator = ruleEvaluator;
        this.eventPublisher = eventPublisher;
    }

    /** Manual decision by an organization reviewer (UC-12, UC-13, BR-030, BR-031). */
    @Transactional
    public Validation decide(UUID submissionId, UUID reviewerId, ValidationDecision decision, String reasonCode, String comment) {
        if (decision == ValidationDecision.AUTO_APPROVED) {
            throw new BusinessException("INVALID_DECISION", HttpStatus.BAD_REQUEST,
                    "AUTO_APPROVED ne peut pas être choisi manuellement.");
        }
        if (decision != ValidationDecision.APPROVED && (reasonCode == null || !RejectionReasons.SUPPORTED.contains(reasonCode))) {
            throw new BusinessException("INVALID_REASON_CODE", HttpStatus.BAD_REQUEST,
                    "Motif requis, parmi : " + RejectionReasons.SUPPORTED + " (BR-031).");
        }

        TaskSubmission submission = submissionService.getSubmission(submissionId);
        Task task = taskService.getTask(submission.getTaskId());
        Campaign campaign = campaignService.getCampaign(task.getCampaignId());
        organizationService.assertActiveMembership(campaign.getOrganizationId(), reviewerId,
                OrganizationRole.OWNER, OrganizationRole.MANAGER);

        TaskParticipation participation = participationService.getParticipation(submission.getParticipationId());
        applyDecision(decision, participation, task, campaign);

        return validationRepository.save(new Validation(submissionId, decision, reasonCode, reviewerId, comment));
    }

    /** UC-11 — invoked by {@link AutoValidationListener}, never throws onto the submit flow. */
    @Transactional
    public void attemptAutoValidation(UUID submissionId, UUID participationId, UUID taskId, Map<String, Object> content) {
        Task task = taskService.getTask(taskId);
        if (!ruleEvaluator.matches(task.getAutoValidationRules(), content)) {
            return;
        }
        Campaign campaign = campaignService.getCampaign(task.getCampaignId());
        TaskParticipation participation = participationService.getParticipation(participationId);

        applyDecision(ValidationDecision.AUTO_APPROVED, participation, task, campaign);
        validationRepository.save(new Validation(submissionId, ValidationDecision.AUTO_APPROVED, null, null,
                "Règles automatiques satisfaites"));
    }

    private void applyDecision(ValidationDecision decision, TaskParticipation participation, Task task, Campaign campaign) {
        switch (decision) {
            case APPROVED, AUTO_APPROVED -> {
                participationService.approve(participation.getId());
                campaignService.recordBudgetConsumption(campaign.getId(), task.getRewardAmount());
                settleReward(participation.getId(), participation.getUserId(), campaign, task.getRewardAmount());
                eventPublisher.publishEvent(new SubmissionApprovedEvent(
                        participation.getId(), task.getId(), campaign.getId(), campaign.getOrganizationId(),
                        participation.getUserId(), task.getRewardAmount(), campaign.getCurrency()));
            }
            case REJECTED -> participationService.reject(participation.getId());
            case CORRECTION_REQUESTED -> participationService.requestCorrection(participation.getId());
        }
    }

    /**
     * BR-030 — moves the reward amount from the organization's reserved budget into the user's
     * available balance. Idempotent against a retried decision: {@code createPending} returns
     * the existing {@link Reward} if this participation was already settled, and the wallet
     * calls are skipped once that reward is no longer {@code PENDING} (both wallet operations
     * are themselves idempotent on their own key, but skipping avoids even attempting them twice).
     */
    private void settleReward(UUID participationId, UUID userId, Campaign campaign, BigDecimal rewardAmount) {
        Reward reward = rewardService.createPending(participationId, rewardAmount, campaign.getCurrency());
        if (reward.getStatus() != RewardStatus.PENDING) {
            return;
        }
        walletService.consumeReserved(WalletOwnerType.ORGANIZATION, campaign.getOrganizationId(), rewardAmount,
                ReferenceType.CAMPAIGN_BUDGET, campaign.getId(), "reward-consume:" + participationId);
        walletService.credit(WalletOwnerType.USER, userId, rewardAmount,
                ReferenceType.REWARD, participationId, "reward-credit:" + participationId);
        rewardService.markCredited(reward.getId());
    }
}
