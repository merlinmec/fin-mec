package com.mecfin.identity.application;

import com.mecfin.identity.domain.PasswordResetToken;
import com.mecfin.identity.domain.SecurityEventType;
import com.mecfin.identity.domain.User;
import com.mecfin.identity.infra.PasswordResetTokenRepository;
import com.mecfin.identity.infra.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Esqueci minha senha" (Fase 18). Regras que importam:
 * <ul>
 *   <li>a resposta é sempre a mesma, exista o e-mail ou não (sem enumeração de contas);</li>
 *   <li>token de 256 bits aleatórios, só o hash SHA-256 no banco, uso único, 30 minutos;</li>
 *   <li>pedir de novo invalida o link anterior;</li>
 *   <li>redefinir derruba todas as sessões (novo carimbo de segurança + sessões apagadas),
 *       zera o bloqueio por tentativas e avisa por e-mail — mas NÃO desliga o 2FA: quem tomou o
 *       e-mail de alguém ainda precisa do celular para entrar.</li>
 * </ul>
 */
@Service
public class PasswordResetService {

    public static final int VALID_MINUTES = 30;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final SecurityEventService securityEvents;
    private final SessionService sessionService;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final String baseUrl;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(UserRepository userRepository, PasswordResetTokenRepository tokenRepository,
            PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy, SecurityEventService securityEvents,
            SessionService sessionService, ApplicationEventPublisher events, Clock clock,
            @Value("${mecfin.app.base-url}") String baseUrl) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.securityEvents = securityEvents;
        this.sessionService = sessionService;
        this.events = events;
        this.clock = clock;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @Transactional
    public void requestReset(String email, ClientInfo client) {
        Optional<User> found = userRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT));
        if (found.isEmpty()) {
            return;
        }
        User user = found.get();
        tokenRepository.deleteAllByUserId(user.getId());
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        tokenRepository.save(new PasswordResetToken(user.getId(), hash(token),
                clock.instant().plus(Duration.ofMinutes(VALID_MINUTES))));
        securityEvents.record(user.getId(), SecurityEventType.PASSWORD_RESET_REQUESTED, client);
        events.publishEvent(new PasswordResetRequestedEvent(user.getEmail(),
                baseUrl + "/redefinir-senha?token=" + token, VALID_MINUTES));
    }

    @Transactional
    public void resetPassword(String token, String newPassword, ClientInfo client) {
        Instant now = clock.instant();
        PasswordResetToken resetToken = tokenRepository.findByTokenHash(hash(token))
                .filter(t -> t.isUsable(now))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Este link de redefinição é inválido ou expirou — peça um novo"));
        User user = userRepository.findById(resetToken.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("Conta não encontrada"));
        passwordPolicy.validate(user.getEmail(), newPassword);
        user.changePassword(passwordEncoder.encode(newPassword));
        user.resetFailedLogins();
        resetToken.markUsed(now);
        tokenRepository.deleteAllByUserId(user.getId());
        sessionService.deleteAll(user.getEmail());
        securityEvents.record(user.getId(), SecurityEventType.PASSWORD_RESET, client);
        events.publishEvent(new SecurityAlertEvent(SecurityAlertEvent.Kind.PASSWORD_RESET, user.getEmail(),
                UserAgents.describe(client.userAgent()), client.ipAddress(), now));
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
