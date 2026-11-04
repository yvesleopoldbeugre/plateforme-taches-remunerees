package com.platform.campaigns.api;

import com.platform.campaigns.api.dto.CampaignResponse;
import com.platform.campaigns.api.dto.CreateCampaignRequest;
import com.platform.campaigns.api.dto.EligibilityCriteriaRequest;
import com.platform.campaigns.api.dto.EligibilityCriteriaResponse;
import com.platform.campaigns.api.dto.RejectCampaignRequest;
import com.platform.campaigns.application.CampaignService;
import com.platform.campaigns.domain.Campaign;
import com.platform.campaigns.domain.CampaignEligibilityCriteria;
import com.platform.common.exception.BusinessException;
import com.platform.tasks.application.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/campaigns")
@Tag(name = "Campagnes", description = "Cycle de vie d'une campagne : brouillon, soumission à revue, "
        + "publication, rejet, annulation (UC-04 à UC-07, BR-010, BR-011).")
public class CampaignController {

    private final CampaignService campaignService;
    private final TaskService taskService;

    public CampaignController(CampaignService campaignService, TaskService taskService) {
        this.campaignService = campaignService;
        this.taskService = taskService;
    }

    @Operation(summary = "Créer une campagne (brouillon)")
    @PostMapping
    public ResponseEntity<CampaignResponse> create(Authentication authentication, @Valid @RequestBody CreateCampaignRequest request) {
        Campaign campaign = campaignService.createCampaign(request.organizationId(), currentUserId(authentication),
                request.title(), request.description(), request.category(), request.startDate(), request.endDate());
        return ResponseEntity.status(HttpStatus.CREATED).body(CampaignResponse.of(campaign));
    }

    @Operation(summary = "Consulter une campagne")
    @GetMapping("/{campaignId}")
    public CampaignResponse get(@PathVariable UUID campaignId) {
        return CampaignResponse.of(campaignService.getCampaign(campaignId));
    }

    @Operation(summary = "Lister les campagnes d'une organisation")
    @GetMapping
    public List<CampaignResponse> listByOrganization(@RequestParam UUID organizationId) {
        return campaignService.listByOrganization(organizationId).stream().map(CampaignResponse::of).toList();
    }

    @Operation(summary = "Définir les critères d'éligibilité", description = "BR-012 — une justification "
            + "textuelle est obligatoire pour cibler par genre.")
    @PutMapping("/{campaignId}/eligibility-criteria")
    public EligibilityCriteriaResponse setEligibilityCriteria(Authentication authentication, @PathVariable UUID campaignId,
                                                                @Valid @RequestBody EligibilityCriteriaRequest request) {
        CampaignEligibilityCriteria criteria = campaignService.setEligibilityCriteria(campaignId, currentUserId(authentication),
                request.minAge(), request.maxAge(), request.gender(), request.genderJustification(),
                request.countries(), request.languages(), request.minReputation());
        return EligibilityCriteriaResponse.of(criteria);
    }

    @Operation(summary = "Consulter les critères d'éligibilité")
    @GetMapping("/{campaignId}/eligibility-criteria")
    public ResponseEntity<EligibilityCriteriaResponse> getEligibilityCriteria(@PathVariable UUID campaignId) {
        CampaignEligibilityCriteria criteria = campaignService.getCriteria(campaignId);
        return criteria == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(EligibilityCriteriaResponse.of(criteria));
    }

    @Operation(summary = "Soumettre la campagne à revue", description = "Exige au moins une tâche définie.")
    @PostMapping("/{campaignId}/submit-for-review")
    public CampaignResponse submitForReview(Authentication authentication, @PathVariable UUID campaignId) {
        long taskCount = taskService.countByCampaign(campaignId);
        Campaign campaign = campaignService.submitForReview(campaignId, currentUserId(authentication), (int) taskCount);
        return CampaignResponse.of(campaign);
    }

    /**
     * BR-010 is now enforced for real: {@code CampaignService#publish} reserves the budget on the
     * organization's wallet and throws {@code INSUFFICIENT_BALANCE} (409) if it can't. Réservé
     * aux rôles plateforme en attendant le module admin (Phase 9).
     */
    @Operation(summary = "Publier une campagne (admin)", description = "Réserve le budget total sur le "
            + "portefeuille de l'organisation (BR-010) — 409 INSUFFICIENT_BALANCE ou 409 "
            + "ORGANIZATION_NOT_VERIFIED si les conditions ne sont pas remplies. PLATFORM_ADMIN ou OPERATOR.")
    @PostMapping("/{campaignId}/publish")
    @PreAuthorize("hasAnyAuthority('ROLE_PLATFORM_ADMIN', 'ROLE_PLATFORM_OPERATOR')")
    public CampaignResponse publish(@PathVariable UUID campaignId) {
        return CampaignResponse.of(campaignService.publish(campaignId));
    }

    @Operation(summary = "Rejeter une campagne en revue (admin)", description = "PLATFORM_ADMIN ou OPERATOR.")
    @PostMapping("/{campaignId}/reject")
    @PreAuthorize("hasAnyAuthority('ROLE_PLATFORM_ADMIN', 'ROLE_PLATFORM_OPERATOR')")
    public CampaignResponse reject(@PathVariable UUID campaignId, @Valid @RequestBody RejectCampaignRequest request) {
        return CampaignResponse.of(campaignService.reject(campaignId, request.reason()));
    }

    @Operation(summary = "Repasser une campagne rejetée en brouillon")
    @PostMapping("/{campaignId}/return-to-draft")
    public CampaignResponse returnToDraft(Authentication authentication, @PathVariable UUID campaignId) {
        return CampaignResponse.of(campaignService.returnToDraft(campaignId, currentUserId(authentication)));
    }

    /** BR-011 — releases whatever was reserved but never consumed back to the organization's wallet. */
    @Operation(summary = "Annuler une campagne", description = "Libère vers le portefeuille de "
            + "l'organisation la part du budget réservée mais jamais consommée (BR-011).")
    @PostMapping("/{campaignId}/cancel")
    public CampaignResponse cancel(Authentication authentication, @PathVariable UUID campaignId) {
        return CampaignResponse.of(campaignService.cancel(campaignId, currentUserId(authentication)));
    }

    private UUID currentUserId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
