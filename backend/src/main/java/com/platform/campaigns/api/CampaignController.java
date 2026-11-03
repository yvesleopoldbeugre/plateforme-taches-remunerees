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
public class CampaignController {

    private final CampaignService campaignService;
    private final TaskService taskService;

    public CampaignController(CampaignService campaignService, TaskService taskService) {
        this.campaignService = campaignService;
        this.taskService = taskService;
    }

    @PostMapping
    public ResponseEntity<CampaignResponse> create(Authentication authentication, @Valid @RequestBody CreateCampaignRequest request) {
        Campaign campaign = campaignService.createCampaign(request.organizationId(), currentUserId(authentication),
                request.title(), request.description(), request.category(), request.startDate(), request.endDate());
        return ResponseEntity.status(HttpStatus.CREATED).body(CampaignResponse.of(campaign));
    }

    @GetMapping("/{campaignId}")
    public CampaignResponse get(@PathVariable UUID campaignId) {
        return CampaignResponse.of(campaignService.getCampaign(campaignId));
    }

    @GetMapping
    public List<CampaignResponse> listByOrganization(@RequestParam UUID organizationId) {
        return campaignService.listByOrganization(organizationId).stream().map(CampaignResponse::of).toList();
    }

    @PutMapping("/{campaignId}/eligibility-criteria")
    public EligibilityCriteriaResponse setEligibilityCriteria(Authentication authentication, @PathVariable UUID campaignId,
                                                                @Valid @RequestBody EligibilityCriteriaRequest request) {
        CampaignEligibilityCriteria criteria = campaignService.setEligibilityCriteria(campaignId, currentUserId(authentication),
                request.minAge(), request.maxAge(), request.gender(), request.genderJustification(),
                request.countries(), request.languages(), request.minReputation());
        return EligibilityCriteriaResponse.of(criteria);
    }

    @GetMapping("/{campaignId}/eligibility-criteria")
    public ResponseEntity<EligibilityCriteriaResponse> getEligibilityCriteria(@PathVariable UUID campaignId) {
        CampaignEligibilityCriteria criteria = campaignService.getCriteria(campaignId);
        return criteria == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(EligibilityCriteriaResponse.of(criteria));
    }

    @PostMapping("/{campaignId}/submit-for-review")
    public CampaignResponse submitForReview(Authentication authentication, @PathVariable UUID campaignId) {
        long taskCount = taskService.countByCampaign(campaignId);
        Campaign campaign = campaignService.submitForReview(campaignId, currentUserId(authentication), (int) taskCount);
        return CampaignResponse.of(campaign);
    }

    /**
     * ⚠ Statut uniquement — ne vérifie pas encore le solde de l'organisation (BR-010), cf.
     * {@link Campaign#publish()}. Réservé aux rôles plateforme en attendant le module admin
     * (Phase 9) ; la réservation budgétaire réelle arrive avec le module wallet (Phase 7).
     */
    @PostMapping("/{campaignId}/publish")
    @PreAuthorize("hasAnyAuthority('ROLE_PLATFORM_ADMIN', 'ROLE_PLATFORM_OPERATOR')")
    public CampaignResponse publish(@PathVariable UUID campaignId) {
        return CampaignResponse.of(campaignService.publish(campaignId));
    }

    @PostMapping("/{campaignId}/reject")
    @PreAuthorize("hasAnyAuthority('ROLE_PLATFORM_ADMIN', 'ROLE_PLATFORM_OPERATOR')")
    public CampaignResponse reject(@PathVariable UUID campaignId, @Valid @RequestBody RejectCampaignRequest request) {
        return CampaignResponse.of(campaignService.reject(campaignId, request.reason()));
    }

    @PostMapping("/{campaignId}/return-to-draft")
    public CampaignResponse returnToDraft(Authentication authentication, @PathVariable UUID campaignId) {
        return CampaignResponse.of(campaignService.returnToDraft(campaignId, currentUserId(authentication)));
    }

    private UUID currentUserId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
