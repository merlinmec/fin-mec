package com.mecfin.identity.application;

import com.mecfin.identity.domain.User;
import com.mecfin.identity.domain.UserRegisteredEvent;
import com.mecfin.identity.infra.UserRepository;
import java.util.Locale;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
            ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public User register(String email, String rawPassword) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new DuplicateEmailException(normalizedEmail);
        }
        User user = new User(normalizedEmail, passwordEncoder.encode(rawPassword));
        User saved;
        try {
            // saveAndFlush (not save) forces the INSERT now, inside this try block, so a
            // unique-constraint violation from a concurrent registration of the same email
            // (the findByEmail check above can't see an uncommitted row) surfaces here as a
            // clean 409 instead of leaking out as an unhandled 500 later at transaction commit.
            saved = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateEmailException(normalizedEmail);
        }
        // Publicado dentro da mesma transação (o listener padrão do Spring é síncrono):
        // se a criação do household (módulo household) falhar, o registro inteiro reverte.
        eventPublisher.publishEvent(new UserRegisteredEvent(saved.getId(), saved.getEmail()));
        return saved;
    }
}
