package com.platform.participations.api;

import com.platform.campaigns.application.CampaignService;
import com.platform.organizations.application.OrganizationService;
import com.platform.participations.api.dto.ParticipationResponse;
import com.platform.participations.api.dto.SubmissionRequest;
import com.platform.participations.application.ParticipationService;
import com.platform.participations.domain.TaskParticipation;
import com.platform.tasks.application.TaskService;
import com.platform.tasks.domain.Task;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Participations", description = "Réservation d'une place sur une tâche, dépôt de preuve, "
        + "annulation (UC-09, UC-10, BR-020 à BR-022).")
public class ParticipationController {

    private final ParticipationService participationService;
    private final TaskService taskService;
    private final CampaignService campaignService;
    private final OrganizationService organizationService;

    public ParticipationController(ParticipationService participationService, TaskService taskService,
                                    CampaignService campaignService, OrganizationService organizationService) {
        this.participationService = participationService;
        this.taskService = taskService;
        this.campaignService = campaignService;
        this.organizationService = organizationService;
    }

    @Operation(summary = "Réserver une place sur une tâche", description = "BR-020/BR-022 — une seule "
            + "participation active par utilisateur et par tâche, jamais plus de places que de capacité, "
            + "garanti par mise à jour SQL conditionnelle, pas verrou pessimiste.")
    @PostMapping("/tasks/{taskId}/participate")
    public ResponseEntity<ParticipationResponse> participate(Authentication authentication, @PathVariable UUID taskId) {
        TaskParticipation participation = participationService.participate(taskId, currentUserId(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(ParticipationResponse.of(participation));
    }

    @Operation(summary = "Lister mes participations")
    @GetMapping("/participations/me")
    public List<ParticipationResponse> myParticipations(Authentication authentication) {
        return participationService.getMyParticipations(currentUserId(authentication)).stream()
                .map(ParticipationResponse::of)
                .toList();
    }

    @Operation(summary = "Consulter une participation", description = "Réservé au participant lui-même.")
    @GetMapping("/participations/{participationId}")
    public ParticipationResponse get(Authentication authentication, @PathVariable UUID participationId) {
        TaskParticipation participation = participationService.getParticipation(participationId);
        participation.assertOwnedBy(currentUserId(authentication));
        return ParticipationResponse.of(participation);
    }

    @Operation(summary = "Déposer une preuve de réalisation", description = "BR-021 — échoue en 410 si le "
            + "délai de réservation est dépassé ; BR-051 — un contenu identique à une soumission antérieure "
            + "sur la même tâche est marqué comme doublon (pas rejeté) et ne sera jamais auto-validé.")
    @PostMapping("/participations/{participationId}/submission")
    public ResponseEntity<ParticipationResponse> submit(Authentication authentication, @PathVariable UUID participationId,
                                                          @Valid @RequestBody SubmissionRequest request) {
        TaskParticipation participation = participationService.submit(
                participationId, currentUserId(authentication), request.content(), request.attachments());
        return ResponseEntity.status(HttpStatus.CREATED).body(ParticipationResponse.of(participation));
    }

    @Operation(summary = "Annuler ma participation", description = "Libère la place réservée.")
    @PostMapping("/participations/{participationId}/cancel")
    public ResponseEntity<Void> cancel(Authentication authentication, @PathVariable UUID participationId) {
        participationService.cancel(participationId, currentUserId(authentication));
        return ResponseEntity.ok().build();
    }

    /** Visible to active members of the task's owning organization only — not the public. */
    @Operation(summary = "Lister les participations d'une tâche", description = "Réservé aux membres actifs "
            + "de l'organisation propriétaire de la tâche.")
    @GetMapping("/tasks/{taskId}/participations")
    public List<ParticipationResponse> listByTask(Authentication authentication, @PathVariable UUID taskId) {
        Task task = taskService.getTask(taskId);
        var campaign = campaignService.getCampaign(task.getCampaignId());
        organizationService.assertActiveMembership(campaign.getOrganizationId(), currentUserId(authentication));
        return participationService.listByTask(taskId).stream().map(ParticipationResponse::of).toList();
    }

    private UUID currentUserId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
