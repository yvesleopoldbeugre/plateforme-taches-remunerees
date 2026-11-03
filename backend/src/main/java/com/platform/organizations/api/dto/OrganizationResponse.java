package com.platform.organizations.api.dto;

import com.platform.organizations.domain.Organization;
import java.time.Instant;
import java.util.UUID;

public record OrganizationResponse(
        UUID id, String name, String legalName, String country,
        String status, String kycStatus, Instant createdAt
) {
    public static OrganizationResponse of(Organization org) {
        return new OrganizationResponse(org.getId(), org.getName(), org.getLegalName(), org.getCountry(),
                org.getStatus().name(), org.getKycStatus().name(), org.getCreatedAt());
    }
}
