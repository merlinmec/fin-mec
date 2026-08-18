package com.mecfin.identity.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.mecfin.household.infra.HouseholdMemberRepository;
import com.mecfin.identity.domain.AuthenticatedUser;
import com.mecfin.identity.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private HouseholdMemberRepository householdMemberRepository;

    @Test
    void loadUserByUsernameNormalizesEmailAndWrapsUserWithHouseholdId() {
        User user = new User("user@example.com", "hash");
        UUID householdId = UUID.randomUUID();
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(householdMemberRepository.findHouseholdIdByUserId(user.getId())).thenReturn(Optional.of(householdId));

        UserDetailsServiceImpl service = new UserDetailsServiceImpl(userRepository, householdMemberRepository);
        UserDetails details = service.loadUserByUsername("User@Example.com");

        assertThat(details).isInstanceOf(AuthenticatedUser.class);
        assertThat(details.getUsername()).isEqualTo("user@example.com");
        assertThat(((AuthenticatedUser) details).getUser()).isSameAs(user);
        assertThat(((AuthenticatedUser) details).getHouseholdId()).isEqualTo(householdId);
    }

    @Test
    void loadUserByUsernameThrowsWhenNotFound() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        UserDetailsServiceImpl service = new UserDetailsServiceImpl(userRepository, householdMemberRepository);

        assertThatThrownBy(() -> service.loadUserByUsername("missing@example.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void loadUserByUsernameThrowsWhenUserHasNoHousehold() {
        User user = new User("user@example.com", "hash");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(householdMemberRepository.findHouseholdIdByUserId(user.getId())).thenReturn(Optional.empty());

        UserDetailsServiceImpl service = new UserDetailsServiceImpl(userRepository, householdMemberRepository);

        assertThatThrownBy(() -> service.loadUserByUsername("user@example.com"))
                .isInstanceOf(IllegalStateException.class);
    }
}
