package com.mecfin.shared.security;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Ponto único de acesso ao usuário autenticado atual. Todo módulo que possui
 * dados pertencentes a um usuário deve escopar suas consultas por
 * {@link #id()} — nunca por um id vindo puro do cliente (mitigação de IDOR/BOLA).
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static UUID id() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthenticatedPrincipal principal)) {
            throw new IllegalStateException("No authenticated user in security context");
        }
        return principal.getUserId();
    }
}
