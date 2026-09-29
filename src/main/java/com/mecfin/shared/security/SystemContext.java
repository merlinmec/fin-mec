package com.mecfin.shared.security;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Executa código de sistema (job agendado, webhook) "em nome" de um household, sem usuário
 * logado. Todo serviço do projeto escopa por CurrentUser.householdId(); em vez de abrir exceções
 * nessas regras, o trabalho de sistema roda com um principal restrito ao household dono do dado
 * — as mesmas checagens de escopo continuam valendo (ex.: a sincronização bancária não consegue
 * gravar em conta de outro household).
 */
public final class SystemContext {

    private record SystemPrincipal(UUID getUserId, UUID getHouseholdId) implements AuthenticatedPrincipal {
    }

    private SystemContext() {
    }

    public static <T> T runAsHousehold(UUID householdId, Supplier<T> action) {
        SecurityContext previous = SecurityContextHolder.getContext();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new SystemPrincipal(null, householdId), null, List.of()));
        SecurityContextHolder.setContext(context);
        try {
            return action.get();
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }
}
