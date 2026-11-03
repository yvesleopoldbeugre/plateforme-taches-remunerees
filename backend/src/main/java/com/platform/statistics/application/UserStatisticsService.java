package com.platform.statistics.application;

import com.platform.participations.application.ParticipationService;
import com.platform.participations.domain.ParticipationStatus;
import com.platform.participations.domain.TaskParticipation;
import com.platform.statistics.api.dto.UserStatisticsResponse;
import com.platform.wallet.application.WalletService;
import com.platform.wallet.domain.Wallet;
import com.platform.wallet.domain.WalletOwnerType;
import com.platform.withdrawals.application.WithdrawalService;
import com.platform.withdrawals.domain.WithdrawalStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Computes {@link UserStatisticsResponse} (UC-20) — see that DTO's javadoc for the formulas chosen. */
@Service
public class UserStatisticsService {

    private final ParticipationService participationService;
    private final WalletService walletService;
    private final WithdrawalService withdrawalService;

    public UserStatisticsService(ParticipationService participationService, WalletService walletService,
                                  WithdrawalService withdrawalService) {
        this.participationService = participationService;
        this.walletService = walletService;
        this.withdrawalService = withdrawalService;
    }

    public UserStatisticsResponse compute(UUID userId) {
        long realized = 0, validated = 0, rejected = 0;
        for (TaskParticipation participation : participationService.getMyParticipations(userId)) {
            if (participation.getSubmittedAt() == null) {
                continue;
            }
            realized++;
            if (participation.getStatus() == ParticipationStatus.APPROVED) {
                validated++;
            } else if (participation.getStatus() == ParticipationStatus.REJECTED) {
                rejected++;
            }
        }

        Wallet wallet = walletService.getOrCreateWallet(WalletOwnerType.USER, userId);
        BigDecimal revenueTotal = wallet.getLifetimeEarned();
        BigDecimal revenueAverage = validated == 0 ? BigDecimal.ZERO
                : revenueTotal.divide(BigDecimal.valueOf(validated), 2, RoundingMode.HALF_UP);

        long withdrawalFrequency = withdrawalService.listByUser(userId).stream()
                .filter(w -> w.getStatus() == WithdrawalStatus.COMPLETED)
                .count();

        BigDecimal successRate = realized == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(validated).divide(BigDecimal.valueOf(realized), 4, RoundingMode.HALF_UP);

        return new UserStatisticsResponse(realized, validated, rejected, successRate, revenueTotal, revenueAverage,
                wallet.getLifetimeWithdrawn(), withdrawalFrequency);
    }
}
