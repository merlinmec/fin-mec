package com.mecfin.identity.api;

import com.mecfin.identity.domain.SecurityEvent;
import com.mecfin.identity.domain.SecurityEventType;
import java.time.Instant;
import java.util.UUID;

public record SecurityEventResponse(UUID id, SecurityEventType type, String ipAddress, String userAgent, Instant createdAt) {

    public static SecurityEventResponse from(SecurityEvent event) {
        return new SecurityEventResponse(event.getId(), event.getType(), event.getIpAddress(), event.getUserAgent(),
                event.getCreatedAt());
    }
}
