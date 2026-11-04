package com.platform.admin.api;

import com.platform.campaigns.api.dto.CampaignResponse;
import com.platform.campaigns.application.CampaignService;
import com.platform.campaigns.domain.CampaignStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Cross-organization campaign moderation queue — see {@code AdminOrganizationQueueController}. */
@RestController
@PreAuthorize("hasAnyAuthority('ROLE_PLATFORM_ADMIN', 'ROLE_PLATFORM_OPERATOR')")
@Tag(name = "Administration", description = "Amorçage du premier compte administrateur, files de "
        + "supervision transverses (organisations, campagnes) et journal d'audit.")
public class AdminCampaignQueueController {

    private final CampaignService campaignService;

    public AdminCampaignQueueController(CampaignService campaignService) {
        this.campaignService = campaignService;
    }

    @Operation(summary = "File des campagnes par statut (admin)", description = "Toutes organisations "
            + "confondues — c'est le critère de validation explicite de la Phase 9.")
    @GetMapping("/api/v1/admin/campaigns")
    public List<CampaignResponse> queue(@RequestParam CampaignStatus status) {
        return campaignService.listByStatus(status).stream().map(CampaignResponse::of).toList();
    }
}
