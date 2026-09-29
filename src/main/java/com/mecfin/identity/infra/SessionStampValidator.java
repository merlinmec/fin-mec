package com.mecfin.identity.infra;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mecfin.identity.domain.AuthenticatedUser;
import com.mecfin.shared.security.AuthenticatedPrincipal;
import com.mecfin.shared.security.SessionValidator;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Compara o carimbo de segurança guardado na sessão com o atual do banco. Cache curto (10s)
 * para não fazer uma consulta por requisição; mudanças feitas por esta mesma instância chamam
 * {@link #evict} e valem na hora. Com várias instâncias, uma sessão revogada em outra instância
 * sobrevive no máximo o TTL do cache.
 */
@Component
public class SessionStampValidator implements SessionValidator {

    private final UserRepository userRepository;
    private final Cache<UUID, Optional<UUID>> stamps = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(10))
            .maximumSize(10_000)
            .build();

    public SessionStampValidator(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public boolean isStillValid(AuthenticatedPrincipal principal) {
        if (!(principal instanceof AuthenticatedUser user)) {
            return true;
        }
        Optional<UUID> current = stamps.get(user.getUserId(), userRepository::findSecurityStampById);
        // Usuário excluído (Optional vazio) também derruba a sessão.
        return current.map(stamp -> stamp.equals(user.getUser().getSecurityStamp())).orElse(false);
    }

    public void evict(UUID userId) {
        stamps.invalidate(userId);
    }
}
