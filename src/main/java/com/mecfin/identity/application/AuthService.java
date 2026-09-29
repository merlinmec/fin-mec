package com.mecfin.identity.application;

import com.mecfin.identity.domain.SecurityEventType;
import com.mecfin.identity.domain.User;
import com.mecfin.identity.domain.UserRegisteredEvent;
import com.mecfin.identity.infra.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
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
    private final PasswordPolicy passwordPolicy;
    private final SecurityEventService securityEvents;
    private final Clock clock;
    private final int maxFailedAttempts;
    private final Duration lockDuration;
    // Hash de uma senha qualquer, calculado uma vez: e-mail inexistente também paga o custo de
    // um Argon2, senão o tempo de resposta revelaria quais e-mails têm conta.
    private final String timingDummyHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
            ApplicationEventPublisher eventPublisher, PasswordPolicy passwordPolicy,
            SecurityEventService securityEvents, Clock clock,
            @Value("${mecfin.security.lockout.max-attempts:5}") int maxFailedAttempts,
            @Value("${mecfin.security.lockout.duration:PT15M}") Duration lockDuration) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
        this.passwordPolicy = passwordPolicy;
        this.securityEvents = securityEvents;
        this.clock = clock;
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockDuration = lockDuration;
        this.timingDummyHash = passwordEncoder.encode("timing-equalization-" + UUID.randomUUID());
    }

    @Transactional
    public User register(String email, String rawPassword) {
        String normalizedEmail = normalize(email);
        passwordPolicy.validate(normalizedEmail, rawPassword);
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

    /**
     * Primeiro fator do login (Fase 14 - antes delegava ao AuthenticationManager). Com a conta
     * bloqueada a senha nem é conferida: senão o bloqueio não impediria testar senhas.
     * noRollbackFor: a falha precisa ficar gravada (contador de tentativas + auditoria) mesmo
     * com a exceção que devolve o 401/429 ao cliente.
     */
    @Transactional(noRollbackFor = {InvalidCredentialsException.class, AccountLockedException.class})
    public User verifyPassword(String email, String rawPassword, ClientInfo client) {
        Optional<User> found = userRepository.findByEmail(normalize(email));
        if (found.isEmpty()) {
            passwordEncoder.matches(rawPassword, timingDummyHash);
            throw new InvalidCredentialsException();
        }
        User user = found.get();
        Instant now = clock.instant();
        if (user.isLocked(now)) {
            throw new AccountLockedException();
        }
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            registerFailure(user, client);
            throw new InvalidCredentialsException();
        }
        return user;
    }

    /** Falha de segundo fator conta para o bloqueio igual a uma senha errada. */
    @Transactional
    public void registerSecondFactorFailure(UUID userId, ClientInfo client) {
        userRepository.findById(userId).ifPresent(user -> {
            securityEvents.record(user.getId(), SecurityEventType.LOGIN_MFA_FAILURE, client);
            if (user.registerFailedLogin(maxFailedAttempts, lockDuration, clock.instant())) {
                securityEvents.record(user.getId(), SecurityEventType.ACCOUNT_LOCKED, client);
            }
        });
    }

    @Transactional
    public void registerSuccess(UUID userId, ClientInfo client) {
        userRepository.findById(userId).ifPresent(user -> {
            user.resetFailedLogins();
            // Fase 18: login de um navegador/dispositivo nunca visto avisa o dono por e-mail —
            // se a senha vazou, é assim que ele descobre.
            if (securityEvents.isNewDevice(userId, client.userAgent())) {
                eventPublisher.publishEvent(new SecurityAlertEvent(SecurityAlertEvent.Kind.NEW_DEVICE_LOGIN,
                        user.getEmail(), UserAgents.describe(client.userAgent()), client.ipAddress(), clock.instant()));
            }
        });
        securityEvents.record(userId, SecurityEventType.LOGIN_SUCCESS, client);
    }

    public boolean isLocked(UUID userId) {
        return userRepository.findById(userId).map(user -> user.isLocked(clock.instant())).orElse(true);
    }

    private void registerFailure(User user, ClientInfo client) {
        securityEvents.record(user.getId(), SecurityEventType.LOGIN_FAILURE, client);
        if (user.registerFailedLogin(maxFailedAttempts, lockDuration, clock.instant())) {
            securityEvents.record(user.getId(), SecurityEventType.ACCOUNT_LOCKED, client);
        }
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
