package com.platform.wallet.infrastructure;

import com.platform.wallet.domain.Reward;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RewardRepository extends JpaRepository<Reward, UUID> {

    Optional<Reward> findByParticipationId(UUID participationId);
}
