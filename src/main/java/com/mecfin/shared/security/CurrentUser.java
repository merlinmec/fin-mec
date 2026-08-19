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
        return principal().getUserId();
    }

    /**
     * Household ativo do usuário autenticado. Todo módulo com dado financeiro
     * (account, category, transaction, bill, credit card, ...) deve escopar suas
     * consultas por este id — nunca por {@link #id()} — para que o dado seja
     * naturalmente compartilhável entre membros do mesmo household no futuro.
     */
    public static UUID householdId() {
        return principal().getHouseholdId();
    }

    private static AuthenticatedPrincipal principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthenticatedPrincipal principal)) {
            throw new IllegalStateException("No authenticated user in security context");
        }
        return principal;
    }
}
