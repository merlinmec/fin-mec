package com.mecfin.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Derruba sessões revogadas (Fase 14). A sessão HTTP guarda um snapshot do usuário no momento
 * do login; trocar a senha, "sair das outras sessões" ou mexer no 2FA muda o carimbo de
 * segurança do usuário, e toda sessão com carimbo antigo é invalidada na próxima requisição -
 * sem precisar de registro central de sessões.
 *
 * A requisição segue como anônima (não responde 401 aqui): rota protegida cai no 401 normal do
 * entry point, e rota pública (ex.: /auth/login de quem voltou para entrar de novo) funciona.
 *
 * Não é @Component de propósito: o Spring Boot registraria qualquer Filter-bean também na
 * cadeia de servlets, e ele rodaria duas vezes (uma fora do Spring Security). É instanciado e
 * posicionado só pelo SecurityConfig.
 */
public class SessionValidityFilter extends OncePerRequestFilter {

    private final SessionValidator sessionValidator;

    public SessionValidityFilter(SessionValidator sessionValidator) {
        this.sessionValidator = sessionValidator;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedPrincipal principal
                && !sessionValidator.isStillValid(principal)) {
            SecurityContextHolder.clearContext();
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
        }
        chain.doFilter(request, response);
    }
}
