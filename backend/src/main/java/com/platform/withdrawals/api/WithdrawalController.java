package com.platform.withdrawals.api;

import com.platform.common.exception.ForbiddenException;
import com.platform.withdrawals.api.dto.RejectWithdrawalRequest;
import com.platform.withdrawals.api.dto.RequestWithdrawalRequest;
import com.platform.withdrawals.api.dto.WithdrawalResponse;
import com.platform.withdrawals.application.WithdrawalService;
import com.platform.withdrawals.domain.Withdrawal;
import com.platform.withdrawals.domain.WithdrawalStatus;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/withdrawals")
public class WithdrawalController {

    private final WithdrawalService withdrawalService;

    public WithdrawalController(WithdrawalService withdrawalService) {
        this.withdrawalService = withdrawalService;
    }

    /** Requires the {@code Idempotency-Key} header — see {@code PaymentController} for why. */
    @PostMapping
    public ResponseEntity<WithdrawalResponse> request(Authentication authentication,
                                                          @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                          @Valid @RequestBody RequestWithdrawalRequest request) {
        Withdrawal withdrawal = withdrawalService.requestWithdrawal(
                currentUserId(authentication), request.paymentMethodId(), request.amount(), idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(WithdrawalResponse.of(withdrawal));
    }

    @GetMapping("/me")
    public List<WithdrawalResponse> mine(Authentication authentication) {
        return withdrawalService.listByUser(currentUserId(authentication)).stream().map(WithdrawalResponse::of).toList();
    }

    /** Operators get the review queue, a plain user can only see their own withdrawal. */
    @GetMapping("/{withdrawalId}")
    public WithdrawalResponse get(Authentication authentication, @PathVariable UUID withdrawalId) {
        Withdrawal withdrawal = withdrawalService.getById(withdrawalId);
        if (!withdrawal.getUserId().equals(currentUserId(authentication)) && !isPlatformOperator(authentication)) {
            throw new ForbiddenException("NOT_WITHDRAWAL_OWNER", "Ce retrait n'appartient pas à cet utilisateur.");
        }
        return WithdrawalResponse.of(withdrawal);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_PLATFORM_ADMIN', 'ROLE_PLATFORM_OPERATOR')")
    public List<WithdrawalResponse> queue(@RequestParam WithdrawalStatus status) {
        return withdrawalService.listByStatus(status).stream().map(WithdrawalResponse::of).toList();
    }

    @PostMapping("/{withdrawalId}/cancel")
    public WithdrawalResponse cancel(Authentication authentication, @PathVariable UUID withdrawalId) {
        return WithdrawalResponse.of(withdrawalService.cancel(withdrawalId, currentUserId(authentication)));
    }

    @PostMapping("/{withdrawalId}/approve")
    @PreAuthorize("hasAnyAuthority('ROLE_PLATFORM_ADMIN', 'ROLE_PLATFORM_OPERATOR')")
    public WithdrawalResponse approve(@PathVariable UUID withdrawalId) {
        return WithdrawalResponse.of(withdrawalService.approve(withdrawalId));
    }

    @PostMapping("/{withdrawalId}/reject")
    @PreAuthorize("hasAnyAuthority('ROLE_PLATFORM_ADMIN', 'ROLE_PLATFORM_OPERATOR')")
    public WithdrawalResponse reject(@PathVariable UUID withdrawalId, @Valid @RequestBody RejectWithdrawalRequest request) {
        return WithdrawalResponse.of(withdrawalService.reject(withdrawalId, request.reason()));
    }

    private boolean isPlatformOperator(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_PLATFORM_ADMIN") || a.getAuthority().equals("ROLE_PLATFORM_OPERATOR"));
    }

    private UUID currentUserId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
