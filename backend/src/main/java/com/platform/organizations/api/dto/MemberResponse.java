package com.platform.organizations.api.dto;

import com.platform.organizations.domain.OrganizationMember;
import com.platform.users.domain.User;
import java.time.Instant;
import java.util.UUID;

public record MemberResponse(
        UUID id, UUID organizationId, UUID userId, String email, String fullName,
        String role, String status, Instant invitedAt, Instant joinedAt
) {
    public static MemberResponse of(OrganizationMember member, User user, String fullName) {
        return new MemberResponse(member.getId(), member.getOrganizationId(), member.getUserId(),
                user.getEmail(), fullName, member.getRole().name(), member.getStatus().name(),
                member.getInvitedAt(), member.getJoinedAt());
    }
}
