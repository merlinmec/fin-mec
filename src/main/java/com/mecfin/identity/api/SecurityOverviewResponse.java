package com.mecfin.identity.api;

import java.time.Instant;

public record SecurityOverviewResponse(
        boolean mfaEnabled, long recoveryCodesRemaining, Instant passwordChangedAt, Instant accountCreatedAt) {
}
