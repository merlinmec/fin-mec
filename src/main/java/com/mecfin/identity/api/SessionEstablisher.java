package com.mecfin.identity.api;

import com.mecfin.identity.domain.AuthenticatedUser;
import com.mecfin.identity.infra.SessionStampValidator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/**
 * Cria (ou renova) a sessão autenticada. Troca o ID da sessão sempre: o login é feito "à mão"
 * (fora do formLogin do Spring Security), então nenhuma SessionAuthenticationStrategy roda -
 * sem o changeSessionId() um ID de sessão plantado antes do login continuaria válido depois
 * dele (session fixation). Achado e corrigido na Fase 14.
 */
@Component
public class SessionEstablisher {

    private final UserDetailsService userDetailsService;
    private final SecurityContextRepository securityContextRepository;
    private final SessionStampValidator sessionStampValidator;
    private final SecurityContextHolderStrategy holder = SecurityContextHolder.getContextHolderStrategy();

    public SessionEstablisher(UserDetailsService userDetailsService, SecurityContextRepository securityContextRepository,
            SessionStampValidator sessionStampValidator) {
        this.userDetailsService = userDetailsService;
        this.securityContextRepository = securityContextRepository;
        this.sessionStampValidator = sessionStampValidator;
    }

    /** Autentica a sessão atual como {@code email}, com um snapshot fresco do usuário. */
    public AuthenticatedUser establish(HttpServletRequest request, HttpServletResponse response, String email) {
        AuthenticatedUser principal = (AuthenticatedUser) userDetailsService.loadUserByUsername(email);
        sessionStampValidator.evict(principal.getUserId());
        if (request.getSession(false) != null) {
            request.changeSessionId();
        } else {
            request.getSession(true);
        }
        SecurityContext context = holder.createEmptyContext();
        context.setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        holder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        return principal;
    }
}
