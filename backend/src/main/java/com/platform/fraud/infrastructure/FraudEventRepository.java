package com.platform.fraud.infrastructure;

import com.platform.fraud.domain.FraudEvent;
import com.platform.fraud.domain.FraudEventStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FraudEventRepository extends JpaRepository<FraudEvent, UUID> {

    List<FraudEvent> findByStatusOrderByDetectedAtAsc(FraudEventStatus status);

    List<FraudEvent> findByUserIdOrderByDetectedAtDesc(UUID userId);
}
