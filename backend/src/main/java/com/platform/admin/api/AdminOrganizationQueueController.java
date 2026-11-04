package com.platform.admin.api;

import com.platform.organizations.api.dto.OrganizationResponse;
import com.platform.organizations.application.OrganizationService;
import com.platform.organizations.domain.OrganizationStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cross-organization reads for the back-office (admin's own package-info: "lecture agrégée pour
 * la supervision plateforme") — {@code OrganizationController}'s own listing is per-member, which
 * can't answer "every organization pending verification" across the whole platform.
 */
@RestController
@PreAuthorize("hasAnyAuthority('ROLE_PLATFORM_ADMIN', 'ROLE_PLATFORM_OPERATOR')")
@Tag(name = "Administration", description = "Amorçage du premier compte administrateur, files de "
        + "supervision transverses (organisations, campagnes) et journal d'audit.")
public class AdminOrganizationQueueController {

    private final OrganizationService organizationService;

    public AdminOrganizationQueueController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @Operation(summary = "File des organisations par statut (admin)", description = "Toutes organisations "
            + "confondues — contrairement à la liste scopée à un membre dans le tag Organisations.")
    @GetMapping("/api/v1/admin/organizations")
    public List<OrganizationResponse> queue(@RequestParam OrganizationStatus status) {
        return organizationService.listByStatus(status).stream().map(OrganizationResponse::of).toList();
    }
}
