package com.mecfin.identity.application;

import com.mecfin.identity.domain.SecurityEventType;
import com.mecfin.identity.domain.User;
import com.mecfin.identity.domain.UserDeletingEvent;
import com.mecfin.identity.infra.RateLimiter;
import com.mecfin.identity.infra.UserRepository;
import java.time.Duration;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operações sensíveis da própria conta (Fase 14). Toda operação destrutiva pede reautenticação
 * (senha + código do 2FA quando ativo) mesmo com sessão válida - sessão roubada ou computador
 * destrancado não bastam para desligar o 2FA ou apagar a conta.
 *
 * Erros de reautenticação são 400, não 401: o SPA trata qualquer 401 como "sessão expirou" e
 * desloga - digitar a senha atual errada não pode derrubar a sessão.
 */
@Service
public class AccountSecurityService {

    static final int REAUTH_ATTEMPTS = 5;
    static final Duration REAUTH_WINDOW = Duration.ofMinutes(15);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final MfaService mfaService;
    private final SecurityEventService securityEvents;
    private final ApplicationEventPublisher eventPublisher;
    private final RateLimiter rateLimiter;

    public AccountSecurityService(UserRepository userRepository, PasswordEncoder passwordEncoder,
            PasswordPolicy passwordPolicy, MfaService mfaService, SecurityEventService securityEvents,
            ApplicationEventPublisher eventPublisher, RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.mfaService = mfaService;
        this.securityEvents = securityEvents;
        this.eventPublisher = eventPublisher;
    }

    /** Troca a senha e derruba todas as outras sessões (novo carimbo de segurança). */
    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword, ClientInfo client) {
        limitAttempts(userId);
        User user = load(userId);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Senha atual incorreta");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("A nova senha precisa ser diferente da atual");
        }
        passwordPolicy.validate(user.getEmail(), newPassword);
        user.changePassword(passwordEncoder.encode(newPassword));
        securityEvents.record(userId, SecurityEventType.PASSWORD_CHANGED, client);
    }

    @Transactional
    public void revokeOtherSessions(UUID userId, ClientInfo client) {
        load(userId).rotateSecurityStamp();
        securityEvents.record(userId, SecurityEventType.SESSIONS_REVOKED, client);
    }

    /** Senha sempre; código do 2FA (ou de recuperação) só se o 2FA estiver ativo. */
    @Transactional
    public void reauthenticate(UUID userId, String password, String code, ClientInfo client) {
        limitAttempts(userId);
        User user = load(userId);
        if (password == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("Senha incorreta");
        }
        if (user.isTotpEnabled() && !mfaService.verifySecondFactor(userId, code, client)) {
            throw new IllegalArgumentException("Código de verificação inválido");
        }
    }

    /**
     * Exclusão definitiva da conta (direito de eliminação, LGPD art. 18, VI). Se o usuário é o
     * único membro do household, todos os dados financeiros vão junto; eventos de segurança e
     * códigos de recuperação caem por ON DELETE CASCADE.
     *
     * O household reage ao evento na MESMA transação (listener síncrono, mesmo padrão do
     * UserRegisteredEvent) - se apagar os dados falhar, o usuário também não é apagado.
     */
    @Transactional
    public void deleteAccount(UUID userId, String password, String code, ClientInfo client) {
        reauthenticate(userId, password, code, client);
        eventPublisher.publishEvent(new UserDeletingEvent(userId));
        userRepository.delete(load(userId));
    }

    /**
     * Teto de tentativas das ações que pedem a senha de novo (trocar senha, excluir conta,
     * desligar 2FA, exportar dados). Achado na Fase 20: nenhuma delas contava erro, então quem
     * roubasse um cookie de sessão podia testar senhas à vontade por ali. Conta toda tentativa,
     * certa ou errada, e fica em memória — um contador no banco seria desfeito pelo rollback da
     * própria transação que falhou.
     */
    private void limitAttempts(UUID userId) {
        if (!rateLimiter.tryConsume("reauth:" + userId, REAUTH_ATTEMPTS, REAUTH_WINDOW)) {
            throw new RateLimitExceededException();
        }
    }

    public User load(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> new IllegalStateException("Usuário não encontrado"));
    }
}
