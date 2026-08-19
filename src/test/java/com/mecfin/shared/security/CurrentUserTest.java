package com.mecfin.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

class CurrentUserTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private record TestPrincipal(UUID getUserId, UUID getHouseholdId) implements AuthenticatedPrincipal {
    }

    @Test
    void idReturnsAuthenticatedPrincipalUserId() {
        UUID userId = UUID.randomUUID();
        AuthenticatedPrincipal principal = new TestPrincipal(userId, UUID.randomUUID());
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThat(CurrentUser.id()).isEqualTo(userId);
    }

    @Test
    void idThrowsWhenNoAuthenticationInContext() {
        assertThatThrownBy(CurrentUser::id).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void householdIdReturnsAuthenticatedPrincipalHouseholdId() {
        UUID householdId = UUID.randomUUID();
        AuthenticatedPrincipal principal = new TestPrincipal(UUID.randomUUID(), householdId);
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThat(CurrentUser.householdId()).isEqualTo(householdId);
    }

    @Test
    void householdIdThrowsWhenNoAuthenticationInContext() {
        assertThatThrownBy(CurrentUser::householdId).isInstanceOf(IllegalStateException.class);
    }
}
