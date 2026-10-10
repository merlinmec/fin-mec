package com.mecfin.demo.api;

import com.mecfin.shared.exception.ForbiddenException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * No deploy da demonstração, desliga o que não faz sentido (ou é arriscado) para um visitante
 * anônimo: criar conta de verdade, mexer em senha e 2FA, convidar alguém por e-mail e enviar
 * arquivos para o banco gratuito. O resto do app funciona igual, inclusive excluir a própria conta
 * e exportar os dados (fictícios).
 *
 * <p>Interceptor, e não filtro: a exceção passa pelo GlobalExceptionHandler e volta no mesmo
 * formato de erro (RFC 7807) que o front já mostra.
 */
@Configuration
@ConditionalOnProperty(name = "mecfin.demo.enabled", havingValue = "true")
public class DemoModeGuard implements WebMvcConfigurer, HandlerInterceptor {

    private record Blocked(HttpMethod method, String pattern) {
    }

    private static final List<Blocked> BLOCKED = List.of(
            new Blocked(HttpMethod.POST, "/api/auth/register"),
            new Blocked(HttpMethod.POST, "/api/auth/password/**"),
            new Blocked(HttpMethod.POST, "/api/account/password"),
            new Blocked(HttpMethod.POST, "/api/account/2fa/**"),
            new Blocked(HttpMethod.POST, "/api/household/invites"),
            new Blocked(HttpMethod.POST, "/api/transactions/*/attachments"),
            new Blocked(HttpMethod.POST, "/api/bank-connections/**"));

    private final AntPathMatcher matcher = new AntPathMatcher();

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();
        boolean blocked = BLOCKED.stream().anyMatch(b ->
                b.method().matches(request.getMethod()) && matcher.match(b.pattern(), path));
        if (blocked) {
            throw new ForbiddenException("Indisponível na demonstração. No app de verdade, isso funciona normalmente.");
        }
        return true;
    }
}
