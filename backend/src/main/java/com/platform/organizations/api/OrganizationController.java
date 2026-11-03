package com.platform.organizations.api;

import com.platform.organizations.api.dto.CreateOrganizationRequest;
import com.platform.organizations.api.dto.InviteMemberRequest;
import com.platform.organizations.api.dto.MemberResponse;
import com.platform.organizations.api.dto.MembershipResponse;
import com.platform.organizations.api.dto.OrganizationResponse;
import com.platform.organizations.application.OrganizationService;
import com.platform.organizations.domain.Organization;
import com.platform.organizations.domain.OrganizationMember;
import com.platform.users.application.UserService;
import com.platform.users.domain.User;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
public class OrganizationController {

    private final OrganizationService organizationService;
    private final UserService userService;

    public OrganizationController(OrganizationService organizationService, UserService userService) {
        this.organizationService = organizationService;
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<OrganizationResponse> create(Authentication authentication,
                                                         @Valid @RequestBody CreateOrganizationRequest request) {
        Organization organization = organizationService.createOrganization(
                currentUserId(authentication), request.name(), request.legalName(), request.country());
        return ResponseEntity.status(HttpStatus.CREATED).body(OrganizationResponse.of(organization));
    }

    @GetMapping("/{organizationId}")
    public OrganizationResponse get(@PathVariable UUID organizationId) {
        return OrganizationResponse.of(organizationService.getOrganization(organizationId));
    }

    @GetMapping("/me/memberships")
    public List<MembershipResponse> myMemberships(Authentication authentication) {
        UUID userId = currentUserId(authentication);
        return organizationService.getMyMemberships(userId).stream()
                .map(member -> MembershipResponse.of(member, organizationService.getOrganization(member.getOrganizationId())))
                .toList();
    }

    @GetMapping("/{organizationId}/members")
    public List<MemberResponse> members(Authentication authentication, @PathVariable UUID organizationId) {
        organizationService.assertActiveMembership(organizationId, currentUserId(authentication));
        return organizationService.getMembers(organizationId).stream()
                .map(this::toMemberResponse)
                .toList();
    }

    @PostMapping("/{organizationId}/members")
    public ResponseEntity<MemberResponse> invite(Authentication authentication, @PathVariable UUID organizationId,
                                                  @Valid @RequestBody InviteMemberRequest request) {
        OrganizationMember member = organizationService.inviteMember(organizationId, currentUserId(authentication), request.email());
        return ResponseEntity.status(HttpStatus.CREATED).body(toMemberResponse(member));
    }

    @PostMapping("/{organizationId}/members/accept")
    public MemberResponse accept(Authentication authentication, @PathVariable UUID organizationId) {
        return toMemberResponse(organizationService.acceptInvitation(organizationId, currentUserId(authentication)));
    }

    @PostMapping("/{organizationId}/members/decline")
    public ResponseEntity<Void> decline(Authentication authentication, @PathVariable UUID organizationId) {
        organizationService.declineInvitation(organizationId, currentUserId(authentication));
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{organizationId}/members/{memberUserId}")
    public ResponseEntity<Void> remove(Authentication authentication, @PathVariable UUID organizationId,
                                        @PathVariable UUID memberUserId) {
        organizationService.removeMember(organizationId, currentUserId(authentication), memberUserId);
        return ResponseEntity.noContent().build();
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
