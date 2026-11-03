package com.platform.payments.infrastructure;

import com.platform.payments.domain.PaymentMethod;
import com.platform.wallet.domain.WalletOwnerType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, UUID> {

    List<PaymentMethod> findByOwnerTypeAndOwnerId(WalletOwnerType ownerType, UUID ownerId);
}
