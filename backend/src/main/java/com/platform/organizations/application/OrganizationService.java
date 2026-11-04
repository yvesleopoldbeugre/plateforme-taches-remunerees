package com.platform.organizations.application;

import com.platform.common.exception.ConflictException;
import com.platform.common.exception.ForbiddenException;
import com.platform.common.exception.NotFoundException;
import com.platform.organizations.domain.KycStatus;
import com.platform.organizations.domain.Organization;
import com.platform.organizations.domain.OrganizationMember;
import com.platform.organizations.domain.OrganizationRole;
import com.platform.organizations.domain.OrganizationStatus;
import com.platform.organizations.domain.event.OrganizationStatusChangedEvent;
import com.platform.organizations.infrastructure.OrganizationMemberRepository;
import com.platform.organizations.infrastructure.OrganizationRepository;
import com.platform.users.application.UserService;
import com.platform.users.domain.User;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for organizations and their membership (BR-003, UC-02).
 *
 * <p>Design decision: an organization in {@code PENDING_VERIFICATION} can still be managed
 * (members invited/accepted/removed) — only {@code SUSPENDED} blocks organization-scoped
 * actions (BR-003 reads "active (non suspendue)"). Turning verification into {@code ACTIVE}
 * is an admin action, not yet built (Phase 9) — it only gates campaign publication (Phase 4),
 * not membership management.
 */
@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository memberRepository;
    private final UserService userService;
    private final ApplicationEventPublisher eventPublisher;

    public OrganizationService(OrganizationRepository organizationRepository,
                                OrganizationMemberRepository memberRepository,
                                UserService userService, ApplicationEventPublisher eventPublisher) {
        this.organizationRepository = organizationRepository;
        this.memberRepository = memberRepository;
        this.userService = userService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Organization createOrganization(UUID ownerUserId, String name, String legalName, String country) {
        Organization organization = organizationRepository.save(new Organization(name, legalName, country, ownerUserId));
        memberRepository.save(OrganizationMember.owner(organization.getId(), ownerUserId));
        return organization;
    }

    public Organization getOrganization(UUID organizationId) {
        return organizationRepository.findById(organizationId)
                .orElseThrow(() -> new NotFoundException("ORGANIZATION_NOT_FOUND", "Organisation introuvable."));
    }

    public List<OrganizationMember> getMembers(UUID organizationId) {
        getOrganization(organizationId);
        return memberRepository.findByOrganizationId(organizationId);
    }

    public List<OrganizationMember> getMyMemberships(UUID userId) {
        return memberRepository.findByUserId(userId);
    }

    /** Phase 12 — CDCT §8: MFA is mandatory for an {@code ORGANIZATION_OWNER}, checked by {@code auth}. */
    public boolean isOwnerOfAnyActiveOrganization(UUID userId) {
        return memberRepository.findByUserId(userId).stream()
                .anyMatch(member -> member.getRole() == OrganizationRole.OWNER && member.isActive());
    }

    @Transactional
    public OrganizationMember inviteMember(UUID organizationId, UUID inviterUserId, String inviteeEmail) {
        assertRole(organizationId, inviterUserId, OrganizationRole.OWNER);
        User invitee = userService.findByEmail(inviteeEmail)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND",
                        "Aucun compte utilisateur n'existe pour cet email — l'invité doit d'abord s'inscrire."));

        memberRepository.findByOrganizationIdAndUserId(organizationId, invitee.getId()).ifPresent(existing -> {
            throw new ConflictException("ALREADY_MEMBER", "Cet utilisateur est déjà membre (ou invité) de l'organisation.");
        });

        return memberRepository.save(OrganizationMember.invite(organizationId, invitee.getId(), OrganizationRole.MANAGER));
    }

    @Transactional
    public OrganizationMember acceptInvitation(UUID organizationId, UUID userId) {
        OrganizationMember member = getMembership(organizationId, userId);
        member.accept();
        return member;
    }

    @Transactional
    public void declineInvitation(UUID organizationId, UUID userId) {
        getMembership(organizationId, userId).decline();
    }

    @Transactional
    public void removeMember(UUID organizationId, UUID requesterId, UUID targetUserId) {
        assertRole(organizationId, requesterId, OrganizationRole.OWNER);
        OrganizationMember target = getMembership(organizationId, targetUserId);
        if (target.getRole() == OrganizationRole.OWNER) {
            throw new ConflictException("CANNOT_REMOVE_OWNER", "Le propriétaire de l'organisation ne peut pas être retiré.");
        }
        target.remove();
    }

    /** Used by other modules (Campaigns, Phase 4+) to authorize organization-scoped actions. */
    public OrganizationMember assertActiveMembership(UUID organizationId, UUID userId, OrganizationRole... allowedRoles) {
        Organization organization = getOrganization(organizationId);
        if (organization.isSuspended()) {
            throw new ForbiddenException("ORGANIZATION_SUSPENDED", "Cette organisation est suspendue.");
        }
        return assertRole(organizationId, userId, allowedRoles);
    }

    private OrganizationMember assertRole(UUID organizationId, UUID userId, OrganizationRole... allowedRoles) {
        OrganizationMember member = memberRepository.findByOrganizationIdAndUserId(organizationId, userId)
                .filter(OrganizationMember::isActive)
                .orElseThrow(() -> new ForbiddenException("NOT_ORGANIZATION_MEMBER",
                        "Vous n'êtes pas membre actif de cette organisation."));
        if (allowedRoles.length > 0 && Arrays.stream(allowedRoles).noneMatch(r -> r == member.getRole())) {
            throw new ForbiddenException("INSUFFICIENT_ORGANIZATION_ROLE", "Votre rôle ne permet pas cette action.");
        }
        return member;
    }

    private OrganizationMember getMembership(UUID organizationId, UUID userId) {
        return memberRepository.findByOrganizationIdAndUserId(organizationId, userId)
                .orElseThrow(() -> new NotFoundException("MEMBERSHIP_NOT_FOUND", "Aucune adhésion trouvée pour cet utilisateur."));
    }

    /** Admin back-office queue (Phase 9) — every organization awaiting verification. */
    public List<Organization> listByStatus(OrganizationStatus status) {
        return organizationRepository.findByStatusOrderByCreatedAtAsc(status);
    }

    @Transactional
    public Organization verify(UUID actorId, UUID organizationId) {
        return changeStatus(actorId, organizationId, null, Organization::verify);
    }

    @Transactional
    public Organization rejectVerification(UUID actorId, UUID organizationId, String reason) {
        return changeStatus(actorId, organizationId, reason, org -> org.rejectVerification(reason));
    }

    @Transactional
    public Organization suspend(UUID actorId, UUID organizationId) {
        return changeStatus(actorId, organizationId, null, Organization::suspend);
    }

    @Transactional
    public Organization reactivate(UUID actorId, UUID organizationId) {
        return changeStatus(actorId, organizationId, null, Organization::reactivate);
    }

    private Organization changeStatus(UUID actorId, UUID organizationId, String reason,
                                       java.util.function.Consumer<Organization> transition) {
        Organization organization = getOrganization(organizationId);
        OrganizationStatus previousStatus = organization.getStatus();
        KycStatus previousKyc = organization.getKycStatus();
        transition.accept(organization);
        eventPublisher.publishEvent(new OrganizationStatusChangedEvent(actorId, organizationId, previousStatus,
                organization.getStatus(), previousKyc, organization.getKycStatus(), reason));
        return organization;
    }
}
