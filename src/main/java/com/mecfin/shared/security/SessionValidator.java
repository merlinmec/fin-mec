package com.mecfin.shared.security;

/**
 * Decide se a sessão de um principal autenticado ainda vale (ex.: a senha foi trocada em outro
 * dispositivo depois do login desta sessão). Interface em shared para o SecurityConfig não
 * depender do módulo identity, que é quem sabe responder.
 */
public interface SessionValidator {

    boolean isStillValid(AuthenticatedPrincipal principal);
}
