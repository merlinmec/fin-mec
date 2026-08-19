package com.mecfin.identity.domain;

import com.mecfin.shared.security.AuthenticatedPrincipal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class AuthenticatedUser implements UserDetails, AuthenticatedPrincipal {

    private final User user;
    private final UUID householdId;

    public AuthenticatedUser(User user, UUID householdId) {
        this.user = user;
        this.householdId = householdId;
    }

    public User getUser() {
        return user;
    }

    @Override
    public UUID getUserId() {
        return user.getId();
    }

    @Override
    public UUID getHouseholdId() {
        return householdId;
    }

    @Override
    public String getUsername() {
        return user.getEmail();
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }
}
