package com.platform.organizations.api;

import com.platform.organizations.api.dto.CreateOrganizationRequest;
import com.platform.organizations.api.dto.InviteMemberRequest;
import com.platform.organizations.api.dto.MemberResponse;
import com.platform.organizations.api.dto.MembershipResponse;
import com.platform.organizations.api.dto.OrganizationResponse;
import com.platform.organizations.api.dto.RejectVerificationRequest;
import com.platform.organizations.application.OrganizationService;
import com.platform.organizations.domain.Organization;
import com.platform.organizations.domain.OrganizationMember;
import com.platform.users.application.UserService;
import com.platform.users.domain.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations")
@Tag(name = "Organisations", description = "Création et gestion d'une organisation, invitations de "
        + "membres, et vérification/suspension administrative (UC-02, UC-21).")
public class OrganizationController {

    private final OrganizationService organizationService;
    private final UserService userService;

    public OrganizationController(OrganizationService organizationService, UserService userService) {
        this.organizationService = organizationService;
        this.userService = userService;
    }

    @Operation(summary = "Créer une organisation", description = "Le créateur en devient immédiatement OWNER (UC-02).")
    @PostMapping
    public ResponseEntity<OrganizationResponse> create(Authentication authentication,
                                                         @Valid @RequestBody CreateOrganizationRequest request) {
        Organization organization = organizationService.createOrganization(
                currentUserId(authentication), request.name(), request.legalName(), request.country());
        return ResponseEntity.status(HttpStatus.CREATED).body(OrganizationResponse.of(organization));
    }

    @Operation(summary = "Consulter une organisation")
    @GetMapping("/{organizationId}")
    public OrganizationResponse get(@PathVariable UUID organizationId) {
        return OrganizationResponse.of(organizationService.getOrganization(organizationId));
    }

    @Operation(summary = "Lister mes adhésions")
    @GetMapping("/me/memberships")
    public List<MembershipResponse> myMemberships(Authentication authentication) {
        UUID userId = currentUserId(authentication);
        return organizationService.getMyMemberships(userId).stream()
                .map(member -> MembershipResponse.of(member, organizationService.getOrganization(member.getOrganizationId())))
                .toList();
    }

    @Operation(summary = "Lister les membres", description = "Réservé aux membres actifs de l'organisation.")
    @GetMapping("/{organizationId}/members")
    public List<MemberResponse> members(Authentication authentication, @PathVariable UUID organizationId) {
        organizationService.assertActiveMembership(organizationId, currentUserId(authentication));
        return organizationService.getMembers(organizationId).stream()
                .map(this::toMemberResponse)
                .toList();
    }

    @Operation(summary = "Inviter un membre", description = "Réservé à OWNER ; l'invité doit déjà avoir un compte USER.")
    @PostMapping("/{organizationId}/members")
    public ResponseEntity<MemberResponse> invite(Authentication authentication, @PathVariable UUID organizationId,
                                                  @Valid @RequestBody InviteMemberRequest request) {
        OrganizationMember member = organizationService.inviteMember(organizationId, currentUserId(authentication), request.email());
        return ResponseEntity.status(HttpStatus.CREATED).body(toMemberResponse(member));
    }

    @Operation(summary = "Accepter une invitation")
    @PostMapping("/{organizationId}/members/accept")
    public MemberResponse accept(Authentication authentication, @PathVariable UUID organizationId) {
        return toMemberResponse(organizationService.acceptInvitation(organizationId, currentUserId(authentication)));
    }

    @Operation(summary = "Refuser une invitation")
    @PostMapping("/{organizationId}/members/decline")
    public ResponseEntity<Void> decline(Authentication authentication, @PathVariable UUID organizationId) {
        organizationService.declineInvitation(organizationId, currentUserId(authentication));
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Retirer un membre", description = "Réservé à OWNER ; le OWNER lui-même ne peut pas être retiré.")
    @DeleteMapping("/{organizationId}/members/{memberUserId}")
    public ResponseEntity<Void> remove(Authentication authentication, @PathVariable UUID organizationId,
                                        @PathVariable UUID memberUserId) {
        organizationService.removeMember(organizationId, currentUserId(authentication), memberUserId);
        return ResponseEntity.noContent().build();
    }

    /** UC-21/US-014 — verification, closing the Phase 3 gap (see {@code CampaignService#publish}). */
    @Operation(summary = "Vérifier une organisation (admin)", description = "Passe l'organisation ACTIVE/"
            + "VERIFIED — condition désormais requise pour publier une campagne (BR-010). "
            + "PLATFORM_ADMIN ou OPERATOR.")
    @PostMapping("/{organizationId}/verify")
    @PreAuthorize("hasAnyAuthority('ROLE_PLATFORM_ADMIN', 'ROLE_PLATFORM_OPERATOR')")
    public OrganizationResponse verify(Authentication authentication, @PathVariable UUID organizationId) {
        return OrganizationResponse.of(organizationService.verify(currentUserId(authentication), organizationId));
    }

    @Operation(summary = "Rejeter la vérification d'une organisation (admin)", description = "Le motif est conservé.")
    @PostMapping("/{organizationId}/reject-verification")
    @PreAuthorize("hasAnyAuthority('ROLE_PLATFORM_ADMIN', 'ROLE_PLATFORM_OPERATOR')")
    public OrganizationResponse rejectVerification(Authentication authentication, @PathVariable UUID organizationId,
                                                      @Valid @RequestBody RejectVerificationRequest request) {
        return OrganizationResponse.of(
                organizationService.rejectVerification(currentUserId(authentication), organizationId, request.reason()));
    }

    /** UC-21 — PLATFORM_ADMIN only, same reasoning as {@code UserController#suspend}. */
    @Operation(summary = "Suspendre une organisation (admin)", description = "Réservé à PLATFORM_ADMIN.")
    @PostMapping("/{organizationId}/suspend")
    @PreAuthorize("hasAuthority('ROLE_PLATFORM_ADMIN')")
    public OrganizationResponse suspend(Authentication authentication, @PathVariable UUID organizationId) {
        return OrganizationResponse.of(organizationService.suspend(currentUserId(authentication), organizationId));
    }

    @Operation(summary = "Réactiver une organisation suspendue (admin)", description = "Retombe sur ACTIVE "
            + "ou PENDING_VERIFICATION selon l'historique de vérification.")
    @PostMapping("/{organizationId}/reactivate")
    @PreAuthorize("hasAuthority('ROLE_PLATFORM_ADMIN')")
    public OrganizationResponse reactivate(Authentication authentication, @PathVariable UUID organizationId) {
        return OrganizationResponse.of(organizationService.reactivate(currentUserId(authentication), organizationId));
    }

    private MemberResponse toMemberResponse(OrganizationMember member) {
        User user = userService.getById(member.getUserId());
        String fullName = userService.getProfile(member.getUserId()).getFullName();
        return MemberResponse.of(member, user, fullName);
    }

    private UUID currentUserId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
