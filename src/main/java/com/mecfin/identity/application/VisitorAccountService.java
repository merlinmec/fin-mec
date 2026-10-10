package com.mecfin.identity.application;

import com.mecfin.identity.domain.User;
import com.mecfin.identity.domain.UserDeletingEvent;
import com.mecfin.identity.infra.SessionStampValidator;
import com.mecfin.identity.infra.UserRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contas temporárias de visitante da demonstração pública (módulo demo). São usuários comuns,
 * criados pelo mesmo cadastro de qualquer conta, mas com e-mail num domínio reservado: o TLD
 * {@code .invalid} (RFC 2606) nunca existe de verdade, então nenhuma pessoa tem um e-mail assim e
 * nada é enviado para ele.
 *
 * <p>Só o módulo demo usa este serviço, e só com {@code mecfin.demo.enabled=true}. Nesse modo o
 * cadastro normal fica bloqueado (DemoModeGuardFilter), então toda conta do banco da demo é de
 * visitante. Mesmo assim, {@link #delete} confere o domínio antes de apagar: um id errado nunca
 * apaga uma conta real.
 */
@Service
public class VisitorAccountService {

    public static final String EMAIL_DOMAIN = "@visitante.fin-mec.invalid";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AuthService authService;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final SessionService sessionService;
    private final SessionStampValidator sessionStampValidator;

    public VisitorAccountService(AuthService authService, UserRepository userRepository,
            ApplicationEventPublisher eventPublisher, SessionService sessionService,
            SessionStampValidator sessionStampValidator) {
        this.authService = authService;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
        this.sessionService = sessionService;
        this.sessionStampValidator = sessionStampValidator;
    }

    /**
     * Cria a conta pelo cadastro normal (household próprio incluído). A senha é aleatória e
     * descartada: o visitante entra pela sessão aberta na hora e nunca precisa dela.
     */
    @Transactional
    public User create() {
        byte[] id = new byte[8];
        RANDOM.nextBytes(id);
        byte[] password = new byte[24];
        RANDOM.nextBytes(password);
        String email = "visitante-" + HexFormat.of().formatHex(id) + EMAIL_DOMAIN;
        return authService.register(email, Base64.getUrlEncoder().withoutPadding().encodeToString(password));
    }

    public long countActive() {
        return userRepository.countByEmailEndingWith(EMAIL_DOMAIN);
    }

    public List<UUID> createdBefore(Instant before) {
        return userRepository.findIdsByEmailEndingWithAndCreatedAtBefore(EMAIL_DOMAIN, before);
    }

    /**
     * Apaga a conta e todos os dados do household, pelo mesmo caminho da exclusão de conta, e
     * derruba as sessões dela na hora: o validador de sessão guarda o carimbo em cache por alguns
     * segundos, então sem isto o visitante continuaria navegando num household que não existe mais.
     */
    @Transactional
    public void delete(UUID userId) {
        userRepository.findById(userId).ifPresent(user -> {
            if (!isVisitor(user.getEmail())) {
                throw new IllegalArgumentException("Só contas de visitante podem ser apagadas por aqui");
            }
            eventPublisher.publishEvent(new UserDeletingEvent(userId));
            userRepository.delete(user);
            sessionService.deleteAll(user.getEmail());
            sessionStampValidator.evict(userId);
        });
    }

    public static boolean isVisitor(String email) {
        return email != null && email.toLowerCase(Locale.ROOT).endsWith(EMAIL_DOMAIN);
    }
}
