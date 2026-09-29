package com.mecfin.shared.config;

import com.mecfin.shared.security.AuthenticatedPrincipal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * "Quem lançou" (Fase 17) via @CreatedBy: cobre todo caminho que cria lançamento (manual,
 * transferência, parcelas, importação) sem cada serviço precisar lembrar de preencher. Trabalho
 * do sistema (sincronização bancária, job de recorrência) roda com principal sem usuário e fica
 * sem autor — a interface mostra como "automático".
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "currentUserAuditor")
public class JpaAuditingConfig {

    @Bean
    AuditorAware<UUID> currentUserAuditor() {
        return () -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedPrincipal principal) {
                return Optional.ofNullable(principal.getUserId());
            }
            return Optional.empty();
        };
    }
}
