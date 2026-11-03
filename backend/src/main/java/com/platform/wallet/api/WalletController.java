package com.platform.wallet.api;

import com.platform.organizations.application.OrganizationService;
import com.platform.organizations.domain.OrganizationRole;
import com.platform.wallet.api.dto.DepositRequest;
import com.platform.wallet.api.dto.WalletResponse;
import com.platform.wallet.api.dto.WalletTransactionResponse;
import com.platform.wallet.application.WalletService;
import com.platform.wallet.domain.ReferenceType;
import com.platform.wallet.domain.Wallet;
import com.platform.wallet.domain.WalletOwnerType;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the wallet/ledger to users and organizations (DAT §4.2, roadmap Phase 7).
 *
 * <p>{@link #deposit} is explicitly a stand-in: it credits an organization's wallet directly, with
 * no provider, webhook or {@code payments} row involved — it exists only so a campaign can be
 * published against a real balance before Phase 8 (Paiements et retraits) builds the actual
 * {@code PaymentProvider} abstraction (H5 in the CDCF) with a manual/simulated adapter and real
 * providers behind it. It deliberately does not touch the {@code payments} table.
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

    @PostMapping("/api/v1/organizations/{organizationId}/wallet/deposit")
    public WalletResponse deposit(Authentication authentication, @PathVariable UUID organizationId,
                                   @Valid @RequestBody DepositRequest request) {
        organizationService.assertActiveMembership(organizationId, currentUserId(authentication), OrganizationRole.OWNER, OrganizationRole.MANAGER);
        return WalletResponse.of(walletService.credit(WalletOwnerType.ORGANIZATION, organizationId, request.amount(),
                ReferenceType.DEPOSIT, null, "manual-deposit:" + UUID.randomUUID()));
    }

    private UUID currentUserId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
