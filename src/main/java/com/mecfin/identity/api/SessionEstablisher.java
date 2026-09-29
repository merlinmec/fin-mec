package com.mecfin.identity.api;

import com.mecfin.identity.application.SessionService;
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
    private final SessionService sessionService;
    private final SecurityContextHolderStrategy holder = SecurityContextHolder.getContextHolderStrategy();

    public SessionEstablisher(UserDetailsService userDetailsService, SecurityContextRepository securityContextRepository,
            SessionStampValidator sessionStampValidator, SessionService sessionService) {
        this.userDetailsService = userDetailsService;
        this.securityContextRepository = securityContextRepository;
        this.sessionStampValidator = sessionStampValidator;
        this.sessionService = sessionService;
    }

    /**
     * Depois de uma mudança que trocou o carimbo de segurança (senha, 2FA, household): apaga as
     * demais sessões do banco e renova a atual com um snapshot novo. O carimbo já as tornaria
     * inválidas na próxima requisição; apagar é higiene (somem da lista "sessões ativas" na hora).
     * A ORDEM importa: o Spring Session só grava o id novo (changeSessionId) no fim da requisição
     * — apagar "as outras" depois da troca apagaria a própria sessão atual, ainda salva sob o id
     * antigo, e deslogaria o usuário.
     */
    public AuthenticatedUser refresh(HttpServletRequest request, HttpServletResponse response, String email) {
        sessionService.deleteOthers(email, request.getSession().getId());
        return establish(request, response, email);
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
        // Para a lista "sessões ativas" (Configurações > Segurança) saber de onde é cada sessão.
        request.getSession().setAttribute(SessionService.USER_AGENT_ATTRIBUTE, truncate(request.getHeader("User-Agent")));
        request.getSession().setAttribute(SessionService.IP_ATTRIBUTE, request.getRemoteAddr());
        return principal;
    }

    private static String truncate(String value) {
        return value == null || value.length() <= 255 ? value : value.substring(0, 255);
    }
}
