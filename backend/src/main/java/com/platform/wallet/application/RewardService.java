package com.platform.wallet.application;

import com.platform.common.exception.NotFoundException;
import com.platform.wallet.domain.Reward;
import com.platform.wallet.infrastructure.RewardRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application service for {@link Reward} — the audit trail of "this approval owes this user money". */
@Service
public class RewardService {

    private final RewardRepository rewardRepository;

    public RewardService(RewardRepository rewardRepository) {
        this.rewardRepository = rewardRepository;
    }

    /** Idempotent: {@code rewards.participation_id} is unique, so a redelivered event is a no-op here. */
    @Transactional
    public Reward createPending(UUID participationId, BigDecimal amount, String currency) {
        return rewardRepository.findByParticipationId(participationId)
                .orElseGet(() -> rewardRepository.save(new Reward(participationId, amount, currency)));
    }

    @Transactional
    public Reward markCredited(UUID rewardId) {
        Reward reward = getById(rewardId);
        reward.markCredited();
        return reward;
    }

    public Reward getById(UUID rewardId) {
        return rewardRepository.findById(rewardId)
                .orElseThrow(() -> new NotFoundException("REWARD_NOT_FOUND", "Récompense introuvable."));
    }

    public Reward getByParticipation(UUID participationId) {
        return rewardRepository.findByParticipationId(participationId)
                .orElseThrow(() -> new NotFoundException("REWARD_NOT_FOUND", "Récompense introuvable."));
    }
}
