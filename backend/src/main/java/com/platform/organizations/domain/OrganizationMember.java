package com.platform.organizations.domain;

import com.platform.common.exception.ConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Maps to {@code organization_members} (DAT §4.2) — one user's role within one organization. */
@Entity
@Table(name = "organization_members")
public class OrganizationMember {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrganizationRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MemberStatus status;

    @Column(name = "invited_at", nullable = false)
    private Instant invitedAt = Instant.now();

    @Column(name = "joined_at")
    private Instant joinedAt;

    protected OrganizationMember() {
        // JPA
    }

    private OrganizationMember(UUID organizationId, UUID userId, OrganizationRole role, MemberStatus status) {
        this.organizationId = organizationId;
        this.userId = userId;
        this.role = role;
        this.status = status;
    }

    /** The creator of an organization becomes its OWNER immediately — no invitation step. */
    public static OrganizationMember owner(UUID organizationId, UUID userId) {
        OrganizationMember member = new OrganizationMember(organizationId, userId, OrganizationRole.OWNER, MemberStatus.ACTIVE);
        member.joinedAt = member.invitedAt;
        return member;
    }

    public static OrganizationMember invite(UUID organizationId, UUID userId, OrganizationRole role) {
        return new OrganizationMember(organizationId, userId, role, MemberStatus.INVITED);
    }

    public void accept() {
        if (status != MemberStatus.INVITED) {
            throw new ConflictException("INVITATION_NOT_PENDING", "Cette invitation n'est plus en attente.");
        }
        this.status = MemberStatus.ACTIVE;
        this.joinedAt = Instant.now();
    }

    public void decline() {
        if (status != MemberStatus.INVITED) {
            throw new ConflictException("INVITATION_NOT_PENDING", "Cette invitation n'est plus en attente.");
        }
        this.status = MemberStatus.REMOVED;
    }

    public void remove() {
        this.status = MemberStatus.REMOVED;
    }

    public boolean isActive() {
        return status == MemberStatus.ACTIVE;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getUserId() {
        return userId;
    }

    public OrganizationRole getRole() {
        return role;
    }

    public MemberStatus getStatus() {
        return status;
    }

    public Instant getInvitedAt() {
        return invitedAt;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }
}
