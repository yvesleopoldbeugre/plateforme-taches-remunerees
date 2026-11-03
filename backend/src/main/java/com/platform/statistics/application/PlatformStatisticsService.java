package com.platform.statistics.application;

import com.platform.fraud.application.FraudService;
import com.platform.fraud.domain.FraudRiskLevel;
import com.platform.organizations.application.OrganizationService;
import com.platform.organizations.domain.OrganizationStatus;
import com.platform.statistics.api.dto.PlatformStatisticsResponse;
import com.platform.users.application.UserService;
import com.platform.users.domain.UserStatus;
import com.platform.wallet.application.WalletService;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

/** Computes {@link PlatformStatisticsResponse} (admin back-office) — see that DTO's javadoc for
 * the two CDCF fields deliberately left out (commissions, litiges). */
@Service
public class PlatformStatisticsService {

    private final UserService userService;
    private final OrganizationService organizationService;
    private final WalletService walletService;
    private final FraudService fraudService;

    public PlatformStatisticsService(UserService userService, OrganizationService organizationService,
                                      WalletService walletService, FraudService fraudService) {
        this.userService = userService;
        this.organizationService = organizationService;
        this.walletService = walletService;
        this.fraudService = fraudService;
    }

    public PlatformStatisticsResponse compute() {
        return new PlatformStatisticsResponse(
                userService.countByStatus(UserStatus.ACTIVE),
                organizationService.listByStatus(OrganizationStatus.ACTIVE).size(),
                walletService.totalCreditedVolume(),
                BigDecimal.ZERO, // BR-044 reportée depuis la Phase 7 — voir la javadoc du DTO.
                fraudService.countByRiskLevel(FraudRiskLevel.LOW),
                fraudService.countByRiskLevel(FraudRiskLevel.MEDIUM),
                fraudService.countByRiskLevel(FraudRiskLevel.HIGH));
    }
}
