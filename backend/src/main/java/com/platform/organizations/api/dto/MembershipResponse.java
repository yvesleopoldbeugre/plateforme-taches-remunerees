package com.platform.organizations.api.dto;

import com.platform.organizations.domain.Organization;
import com.platform.organizations.domain.OrganizationMember;
import java.util.UUID;

public record MembershipResponse(
        UUID organizationId, String organizationName, String role, String status
) {
    public static MembershipResponse of(OrganizationMember member, Organization organization) {
        return new MembershipResponse(organization.getId(), organization.getName(),
                member.getRole().name(), member.getStatus().name());
    }
}
