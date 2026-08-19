package com.mecfin.shared.security;

import java.util.UUID;

public interface AuthenticatedPrincipal {

    UUID getUserId();

    UUID getHouseholdId();
}
