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

    @Test
    void idReturnsAuthenticatedPrincipalUserId() {
        UUID userId = UUID.randomUUID();
        AuthenticatedPrincipal principal = () -> userId;
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThat(CurrentUser.id()).isEqualTo(userId);
    }

    @Test
    void idThrowsWhenNoAuthenticationInContext() {
        assertThatThrownBy(CurrentUser::id).isInstanceOf(IllegalStateException.class);
    }
}
