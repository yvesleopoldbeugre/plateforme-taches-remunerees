package com.platform.tasks.application;

import com.platform.campaigns.application.CampaignService;
import com.platform.campaigns.domain.Campaign;
import com.platform.common.exception.BusinessException;
import com.platform.common.exception.NotFoundException;
import com.platform.tasks.domain.ProofTypes;
import com.platform.tasks.domain.Task;
import com.platform.tasks.domain.TaskTypes;
import com.platform.tasks.infrastructure.TaskRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application service for tasks (UC-05, BR-022's max_participants invariant set up here). */
@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final CampaignService campaignService;

    public TaskService(TaskRepository taskRepository, CampaignService campaignService) {
        this.taskRepository = taskRepository;
        this.campaignService = campaignService;
    }

    @Transactional
    public Task addTask(UUID campaignId, UUID requesterId, String type, String title, String instructions,
                         BigDecimal rewardAmount, Integer maxParticipants, String expectedProofType,
                         String autoValidationRules, Integer reservationTtlMinutes) {
        Campaign campaign = campaignService.assertEditableByMember(campaignId, requesterId);

        if (!TaskTypes.SUPPORTED.contains(type)) {
            throw new BusinessException("UNSUPPORTED_TASK_TYPE", HttpStatus.BAD_REQUEST,
                    "Type de tâche non supporté. Valeurs possibles : " + TaskTypes.SUPPORTED);
        }
        if (!ProofTypes.SUPPORTED.contains(expectedProofType)) {
            throw new BusinessException("UNSUPPORTED_PROOF_TYPE", HttpStatus.BAD_REQUEST,
                    "Type de preuve non supporté. Valeurs possibles : " + ProofTypes.SUPPORTED);
        }
        if (rewardAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("INVALID_REWARD_AMOUNT", HttpStatus.BAD_REQUEST, "La récompense doit être positive.");
        }
        if (maxParticipants == null || maxParticipants <= 0) {
            throw new BusinessException("INVALID_MAX_PARTICIPANTS", HttpStatus.BAD_REQUEST,
                    "Le nombre de places doit être supérieur à zéro (BR-013).");
        }

        Task task = taskRepository.save(new Task(campaignId, type, title, instructions, rewardAmount,
                maxParticipants, expectedProofType, autoValidationRules, reservationTtlMinutes));
        campaignService.recalculateBudget(campaignId, task.totalBudget());
        return task;
    }

    public Task getTask(UUID taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundException("TASK_NOT_FOUND", "Tâche introuvable."));
    }

    public List<Task> listByCampaign(UUID campaignId) {
        return taskRepository.findByCampaignId(campaignId);
    }

    public long countByCampaign(UUID campaignId) {
        return taskRepository.countByCampaignId(campaignId);
    }
}
