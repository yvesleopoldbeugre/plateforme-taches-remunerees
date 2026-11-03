package com.platform.statistics.application;

import com.platform.campaigns.application.CampaignService;
import com.platform.campaigns.domain.Campaign;
import com.platform.campaigns.domain.CampaignStatus;
import com.platform.organizations.application.OrganizationService;
import com.platform.participations.application.ParticipationService;
import com.platform.participations.domain.ParticipationStatus;
import com.platform.participations.domain.TaskParticipation;
import com.platform.statistics.api.dto.OrganizationStatisticsResponse;
import com.platform.tasks.application.TaskService;
import com.platform.tasks.domain.Task;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Computes {@link OrganizationStatisticsResponse} (UC-19) by composing the existing
 * {@code campaigns}/{@code tasks}/{@code participations} services — see this class's own
 * package-info and the response DTO's javadoc for the rate formulas chosen.
 */
@Service
public class OrganizationStatisticsService {

    private final OrganizationService organizationService;
    private final CampaignService campaignService;
    private final TaskService taskService;
    private final ParticipationService participationService;

    public OrganizationStatisticsService(OrganizationService organizationService, CampaignService campaignService,
                                          TaskService taskService, ParticipationService participationService) {
        this.organizationService = organizationService;
        this.campaignService = campaignService;
        this.taskService = taskService;
        this.participationService = participationService;
    }

    public OrganizationStatisticsResponse compute(UUID organizationId, UUID requesterId) {
        organizationService.assertActiveMembership(organizationId, requesterId);

        List<Campaign> campaigns = campaignService.listByOrganization(organizationId);
        long campaignsActive = campaigns.stream().filter(c -> c.getStatus() == CampaignStatus.PUBLISHED).count();
        BigDecimal budgetSpent = campaigns.stream().map(Campaign::getBudgetConsumed).reduce(BigDecimal.ZERO, BigDecimal::add);

        long total = 0, approved = 0, rejected = 0, abandoned = 0;
        for (Campaign campaign : campaigns) {
            for (Task task : taskService.listByCampaign(campaign.getId())) {
                for (TaskParticipation participation : participationService.listByTask(task.getId())) {
                    total++;
                    ParticipationStatus status = participation.getStatus();
                    if (status == ParticipationStatus.APPROVED) {
                        approved++;
                    } else if (status == ParticipationStatus.REJECTED) {
                        rejected++;
                    } else if (status == ParticipationStatus.CANCELLED || status == ParticipationStatus.EXPIRED) {
                        abandoned++;
                    }
                }
            }
        }
        long decided = approved + rejected;

        return new OrganizationStatisticsResponse(
                campaigns.size(),
                campaignsActive,
                total,
                ratio(approved, decided),
                ratio(rejected, decided),
                ratio(abandoned, total),
                budgetSpent,
                approved == 0 ? BigDecimal.ZERO : budgetSpent.divide(BigDecimal.valueOf(approved), 2, RoundingMode.HALF_UP));
    }

    private BigDecimal ratio(long numerator, long denominator) {
        if (denominator == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
    }
}
