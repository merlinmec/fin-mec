package com.mecfin.identity.infra;

import com.mecfin.household.infra.HouseholdMemberRepository;
import com.mecfin.identity.domain.AuthenticatedUser;
import com.mecfin.identity.domain.User;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;
    private final HouseholdMemberRepository householdMemberRepository;

    public UserDetailsServiceImpl(UserRepository userRepository, HouseholdMemberRepository householdMemberRepository) {
        this.userRepository = userRepository;
        this.householdMemberRepository = householdMemberRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmail(normalized)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + normalized));
        // Todo usuário ganha um household no registro (ver HouseholdService); se este lookup
        // falhar é um estado inconsistente do banco, não uma condição esperada de negócio.
        UUID householdId = householdMemberRepository.findHouseholdIdByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("User without household: " + user.getId()));
        return new AuthenticatedUser(user, householdId);
    }
}
