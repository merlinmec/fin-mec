package com.mecfin.identity.api;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

// Estado do login entre a senha correta e o código do 2FA, guardado na sessão (ainda anônima).
record PendingMfaLogin(UUID userId, String email, Instant expiresAt, int attempts) implements Serializable {

    static final String SESSION_ATTRIBUTE = "mecfin.pendingMfaLogin";
    static final int MAX_ATTEMPTS = 5;

    PendingMfaLogin withAttempt() {
        return new PendingMfaLogin(userId, email, expiresAt, attempts + 1);
    }
}
