package com.mecfin.identity.api;

import com.mecfin.identity.domain.User;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(UUID id, String email, Instant createdAt, boolean mfaEnabled) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getCreatedAt(), user.isTotpEnabled());
    }
}
