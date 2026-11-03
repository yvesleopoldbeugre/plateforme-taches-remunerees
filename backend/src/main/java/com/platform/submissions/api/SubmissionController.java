package com.platform.submissions.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.campaigns.application.CampaignService;
import com.platform.organizations.application.OrganizationService;
import com.platform.participations.application.ParticipationService;
import com.platform.participations.domain.TaskParticipation;
import com.platform.submissions.api.dto.TaskSubmissionResponse;
import com.platform.submissions.application.SubmissionService;
import com.platform.tasks.application.TaskService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lets a participant see their own proof, and an organization reviewer see the proof for any
 * participation on one of their tasks (UC-12 — "consulter les réponses, les images...").
 */
@RestController
@RequestMapping("/api/v1/participations/{participationId}/submissions")
public class SubmissionController {

    private final SubmissionService submissionService;
    private final ParticipationService participationService;
    private final TaskService taskService;
    private final CampaignService campaignService;
    private final OrganizationService organizationService;
    private final ObjectMapper objectMapper;

    public SubmissionController(SubmissionService submissionService, ParticipationService participationService,
                                 TaskService taskService, CampaignService campaignService,
                                 OrganizationService organizationService, ObjectMapper objectMapper) {
        this.submissionService = submissionService;
        this.participationService = participationService;
        this.taskService = taskService;
        this.campaignService = campaignService;
        this.organizationService = organizationService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public List<TaskSubmissionResponse> list(Authentication authentication, @PathVariable UUID participationId) {
        UUID requesterId = UUID.fromString(authentication.getName());
        TaskParticipation participation = participationService.getParticipation(participationId);
        assertCanView(participation, requesterId);
        return submissionService.listByParticipation(participationId).stream()
                .map(s -> TaskSubmissionResponse.of(s, objectMapper))
                .toList();
    }

    private void assertCanView(TaskParticipation participation, UUID requesterId) {
        if (participation.getUserId().equals(requesterId)) {
            return;
        }
        var task = taskService.getTask(participation.getTaskId());
        var campaign = campaignService.getCampaign(task.getCampaignId());
        organizationService.assertActiveMembership(campaign.getOrganizationId(), requesterId);
    }
}
