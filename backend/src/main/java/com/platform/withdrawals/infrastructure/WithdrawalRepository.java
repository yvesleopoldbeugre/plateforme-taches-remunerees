package com.platform.withdrawals.infrastructure;

import com.platform.withdrawals.domain.Withdrawal;
import com.platform.withdrawals.domain.WithdrawalStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WithdrawalRepository extends JpaRepository<Withdrawal, UUID> {

    Optional<Withdrawal> findByIdempotencyKey(String idempotencyKey);

    List<Withdrawal> findByUserIdOrderByRequestedAtDesc(UUID userId);

    List<Withdrawal> findByStatusOrderByRequestedAtAsc(WithdrawalStatus status);
}
