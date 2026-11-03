package com.platform.wallet.api;

import com.platform.organizations.application.OrganizationService;
import com.platform.wallet.api.dto.WalletResponse;
import com.platform.wallet.api.dto.WalletTransactionResponse;
import com.platform.wallet.application.WalletService;
import com.platform.wallet.domain.Wallet;
import com.platform.wallet.domain.WalletOwnerType;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the wallet/ledger to users and organizations (DAT §4.2, roadmap Phase 7). Crediting a
 * wallet from the outside is not done here: organization deposits go through
 * {@code PaymentController} (Phase 8, {@code payments} module) and user payouts through
 * {@code WithdrawalController} (Phase 8, {@code withdrawals} module) — this controller is
 * read-only on purpose.
 */
@RestController
public class WalletController {

    private final WalletService walletService;
    private final OrganizationService organizationService;

    public WalletController(WalletService walletService, OrganizationService organizationService) {
        this.walletService = walletService;
        this.organizationService = organizationService;
    }

    @GetMapping("/api/v1/wallet/me")
    public WalletResponse myWallet(Authentication authentication) {
        return WalletResponse.of(walletService.getOrCreateWallet(WalletOwnerType.USER, currentUserId(authentication)));
    }

    @GetMapping("/api/v1/wallet/me/transactions")
    public List<WalletTransactionResponse> myTransactions(Authentication authentication) {
        Wallet wallet = walletService.getOrCreateWallet(WalletOwnerType.USER, currentUserId(authentication));
        return walletService.listTransactions(wallet.getId()).stream().map(WalletTransactionResponse::of).toList();
    }

    @GetMapping("/api/v1/organizations/{organizationId}/wallet")
    public WalletResponse organizationWallet(Authentication authentication, @PathVariable UUID organizationId) {
        organizationService.assertActiveMembership(organizationId, currentUserId(authentication));
        return WalletResponse.of(walletService.getOrCreateWallet(WalletOwnerType.ORGANIZATION, organizationId));
    }

    @GetMapping("/api/v1/organizations/{organizationId}/wallet/transactions")
    public List<WalletTransactionResponse> organizationTransactions(Authentication authentication, @PathVariable UUID organizationId) {
        organizationService.assertActiveMembership(organizationId, currentUserId(authentication));
        Wallet wallet = walletService.getOrCreateWallet(WalletOwnerType.ORGANIZATION, organizationId);
        return walletService.listTransactions(wallet.getId()).stream().map(WalletTransactionResponse::of).toList();
    }

    private UUID currentUserId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
