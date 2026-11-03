package com.platform.campaigns.application;

import com.platform.campaigns.domain.Campaign;
import com.platform.campaigns.domain.CampaignEligibilityCriteria;
import com.platform.campaigns.infrastructure.CampaignEligibilityCriteriaRepository;
import com.platform.campaigns.infrastructure.CampaignRepository;
import com.platform.common.exception.BusinessException;
import com.platform.common.exception.ConflictException;
import com.platform.common.exception.NotFoundException;
import com.platform.organizations.application.OrganizationService;
import com.platform.organizations.domain.OrganizationRole;
import com.platform.wallet.application.WalletService;
import com.platform.wallet.domain.ReferenceType;
import com.platform.wallet.domain.WalletOwnerType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for campaigns (UC-04 to UC-06, BR-012, BR-013). Exposed to other modules
 * (notably {@code tasks}) so they never touch {@link CampaignRepository} directly (DAT §8.2).
 * Depends on {@code wallet} (a noyau module) one-directionally, since Phase 7, for BR-010.
 */
@Service
public class CampaignService {

    private final CampaignRepository campaignRepository;
    private final CampaignEligibilityCriteriaRepository criteriaRepository;
    private final OrganizationService organizationService;
    private final WalletService walletService;

    public CampaignService(CampaignRepository campaignRepository,
                            CampaignEligibilityCriteriaRepository criteriaRepository,
                            OrganizationService organizationService,
                            WalletService walletService) {
        this.campaignRepository = campaignRepository;
        this.criteriaRepository = criteriaRepository;
        this.organizationService = organizationService;
        this.walletService = walletService;
    }

    @Transactional
    public Campaign createCampaign(UUID organizationId, UUID requesterId, String title, String description,
                                    String category, Instant startDate, Instant endDate) {
        organizationService.assertActiveMembership(organizationId, requesterId, OrganizationRole.OWNER, OrganizationRole.MANAGER);
        if (!endDate.isAfter(startDate)) {
            throw new BusinessException("INVALID_CAMPAIGN_DATES", HttpStatus.BAD_REQUEST,
                    "La date de fin doit être postérieure à la date de début (BR-013).");
        }
        return campaignRepository.save(new Campaign(organizationId, title, description, category, startDate, endDate, requesterId));
    }

    public Campaign getCampaign(UUID campaignId) {
        return campaignRepository.findById(campaignId)
                .orElseThrow(() -> new NotFoundException("CAMPAIGN_NOT_FOUND", "Campagne introuvable."));
    }

    public List<Campaign> listByOrganization(UUID organizationId) {
        return campaignRepository.findByOrganizationId(organizationId);
    }

    public CampaignEligibilityCriteria getCriteria(UUID campaignId) {
        return criteriaRepository.findById(campaignId).orElse(null);
    }

    @Transactional
    public CampaignEligibilityCriteria setEligibilityCriteria(UUID campaignId, UUID requesterId, Short minAge, Short maxAge,
                                                                String gender, String genderJustification,
                                                                List<String> countries, List<String> languages,
                                                                BigDecimal minReputation) {
        Campaign campaign = getCampaign(campaignId);
        organizationService.assertActiveMembership(campaign.getOrganizationId(), requesterId,
                OrganizationRole.OWNER, OrganizationRole.MANAGER);
        campaign.assertEditable();

        if (gender != null && !gender.isBlank() && (genderJustification == null || genderJustification.isBlank())) {
            throw new BusinessException("GENDER_JUSTIFICATION_REQUIRED", HttpStatus.BAD_REQUEST,
                    "Une justification est obligatoire pour cibler une campagne par genre (BR-012).");
        }
        criteriaRepository.findById(campaignId).ifPresent(criteriaRepository::delete);
        criteriaRepository.flush();
        return criteriaRepository.save(new CampaignEligibilityCriteria(
                campaignId, minAge, maxAge, gender, genderJustification, countries, languages, minReputation));
    }

    /** Called by the {@code tasks} module whenever a task is added/changed (DAT §9 — budget always derived). */
    @Transactional
    public void recalculateBudget(UUID campaignId, BigDecimal delta) {
        Campaign campaign = getCampaign(campaignId);
        campaign.setBudgetTotal(campaign.getBudgetTotal().add(delta));
    }

    /** Used by the {@code tasks} module to authorize and gate task changes on the DRAFT status. */
    public Campaign assertEditableByMember(UUID campaignId, UUID requesterId) {
        Campaign campaign = getCampaign(campaignId);
        organizationService.assertActiveMembership(campaign.getOrganizationId(), requesterId,
                OrganizationRole.OWNER, OrganizationRole.MANAGER);
        campaign.assertEditable();
        return campaign;
    }

    @Transactional
    public Campaign submitForReview(UUID campaignId, UUID requesterId, int taskCount) {
        Campaign campaign = getCampaign(campaignId);
        organizationService.assertActiveMembership(campaign.getOrganizationId(), requesterId,
                OrganizationRole.OWNER, OrganizationRole.MANAGER);
        if (taskCount == 0) {
            throw new ConflictException("CAMPAIGN_HAS_NO_TASKS", "Ajoutez au moins une tâche avant de soumettre la campagne à revue.");
        }
        campaign.submitForReview();
        return campaign;
    }

    /**
     * Platform-role authorization is enforced by {@code @PreAuthorize} on the controller, not
     * here. BR-010 is enforced right here, before the state transition: the organization's
     * available balance must cover the campaign's budget, and that amount is actually reserved
     * on its wallet — closing the debt flagged since Phase 5 (see {@link Campaign#publish()}).
     */
    @Transactional
    public Campaign publish(UUID campaignId) {
        Campaign campaign = getCampaign(campaignId);
        walletService.reserve(WalletOwnerType.ORGANIZATION, campaign.getOrganizationId(), campaign.getBudgetTotal(),
                ReferenceType.CAMPAIGN_BUDGET, campaignId, "campaign-reserve:" + campaignId);
        campaign.publish();
        return campaign;
    }

    @Transactional
    public Campaign reject(UUID campaignId, String reason) {
        Campaign campaign = getCampaign(campaignId);
        campaign.reject(reason);
        return campaign;
    }

    @Transactional
    public Campaign returnToDraft(UUID campaignId, UUID requesterId) {
        Campaign campaign = getCampaign(campaignId);
        organizationService.assertActiveMembership(campaign.getOrganizationId(), requesterId,
                OrganizationRole.OWNER, OrganizationRole.MANAGER);
        campaign.returnToDraft();
        return campaign;
    }

    /**
     * BR-030 bookkeeping — called by {@code ValidationService} synchronously with the approval
     * decision (not via the event, which only reaches {@code wallet}): the campaign's own
     * reserved/consumed view must never lag behind the decision that caused it.
     */
    @Transactional
    public void recordBudgetConsumption(UUID campaignId, BigDecimal amount) {
        getCampaign(campaignId).consumeBudget(amount);
    }

    /**
     * BR-011 — cancelling a campaign returns whatever was reserved but never consumed to the
     * organization's wallet. No-op on the wallet when nothing is left to release (e.g. a DRAFT
     * campaign that was never published).
     */
    @Transactional
    public Campaign cancel(UUID campaignId, UUID requesterId) {
        Campaign campaign = getCampaign(campaignId);
        organizationService.assertActiveMembership(campaign.getOrganizationId(), requesterId,
                OrganizationRole.OWNER, OrganizationRole.MANAGER);
        BigDecimal toRelease = campaign.unconsumedReservedBudget();
        campaign.cancel();
        if (toRelease.compareTo(BigDecimal.ZERO) > 0) {
            walletService.release(WalletOwnerType.ORGANIZATION, campaign.getOrganizationId(), toRelease,
                    ReferenceType.CAMPAIGN_BUDGET, campaignId, "campaign-cancel:" + campaignId);
        }
        return campaign;
    }
}
