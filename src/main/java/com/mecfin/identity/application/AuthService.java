package com.mecfin.identity.application;

import com.mecfin.identity.domain.User;
import com.mecfin.identity.infra.UserRepository;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User register(String email, String rawPassword) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new DuplicateEmailException(normalizedEmail);
        }
        User user = new User(normalizedEmail, passwordEncoder.encode(rawPassword));
        try {
            // saveAndFlush (not save) forces the INSERT now, inside this try block, so a
            // unique-constraint violation from a concurrent registration of the same email
            // (the findByEmail check above can't see an uncommitted row) surfaces here as a
            // clean 409 instead of leaking out as an unhandled 500 later at transaction commit.
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateEmailException(normalizedEmail);
        }
    }
}
