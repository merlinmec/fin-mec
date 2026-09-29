package com.mecfin.household.domain;

import java.time.Instant;

public record HouseholdInviteCreatedEvent(String email, String invitedByEmail, String householdName, String acceptUrl,
        Instant expiresAt) {
}
