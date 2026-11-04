package com.platform.auth.application;

import com.platform.users.domain.UserStatus;
import com.platform.users.domain.event.UserStatusChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * A suspended/banned account's existing refresh token was already blocked at
 * {@code LoginService#refresh} (BR-002), but its current ≤15-minute access token stays valid
 * until it naturally expires — a JWT isn't re-checked against the database per request. Revoking
 * every refresh token the moment the status changes at least closes the door on minting new
 * sessions immediately, rather than only on the next refresh attempt. {@code users} publishes,
 * {@code auth} listens — same direction as {@code audit}'s listener, not the reverse: {@code users}
 * never depends on {@code auth} (that would cycle back against {@code auth} → {@code users}).
 */
@Component
public class SessionRevocationListener {

    private static final Logger log = LoggerFactory.getLogger(SessionRevocationListener.class);

    private final RefreshTokenService refreshTokenService;

    public SessionRevocationListener(RefreshTokenService refreshTokenService) {
        this.refreshTokenService = refreshTokenService;
    }

    @EventListener
    public void onUserStatusChanged(UserStatusChangedEvent event) {
        if (event.newStatus() != UserStatus.SUSPENDED && event.newStatus() != UserStatus.BANNED) {
            return;
        }
        try {
            refreshTokenService.revokeAllForUser(event.userId());
        } catch (RuntimeException e) {
            log.warn("Failed to revoke sessions for user {} after status change to {}", event.userId(), event.newStatus(), e);
        }
    }
}
