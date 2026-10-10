package com.mecfin.demo.api;

import com.mecfin.demo.application.DemoService;
import com.mecfin.identity.api.SessionEstablisher;
import com.mecfin.identity.api.UserResponse;
import com.mecfin.identity.application.RateLimitExceededException;
import com.mecfin.identity.domain.User;
import com.mecfin.identity.infra.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /demo/status diz ao front se este deploy é a demonstração (mostra "Entrar como visitante" e
 * esconde o cadastro). POST /demo/session cria a conta de visitante, já com dados, e abre a sessão.
 * As duas rotas são públicas (SecurityConfig); a segunda ainda exige o token CSRF, como o login.
 */
@RestController
@RequestMapping("/demo")
public class DemoController {

    public record DemoStatus(boolean enabled) {
    }

    private final Optional<DemoService> demoService;
    private final SessionEstablisher sessionEstablisher;
    private final RateLimiter rateLimiter;
    private final int sessionsPerHour;

    public DemoController(Optional<DemoService> demoService, SessionEstablisher sessionEstablisher,
            RateLimiter rateLimiter, @Value("${mecfin.demo.sessions-per-ip-per-hour:10}") int sessionsPerHour) {
        this.demoService = demoService;
        this.sessionEstablisher = sessionEstablisher;
        this.rateLimiter = rateLimiter;
        this.sessionsPerHour = sessionsPerHour;
    }

    @GetMapping("/status")
    public DemoStatus status() {
        return new DemoStatus(demoService.isPresent());
    }

    @PostMapping("/session")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse start(HttpServletRequest request, HttpServletResponse response) {
        DemoService demo = demoService.orElseThrow(DemoDisabledException::new);
        // Cada sessão cria uma conta inteira no banco: o teto por IP impede encher o banco gratuito.
        if (!rateLimiter.tryConsume("demo:" + request.getRemoteAddr(), sessionsPerHour, Duration.ofHours(1))) {
            throw new RateLimitExceededException();
        }
        User visitor = demo.createVisitor();
        UserResponse user = UserResponse.from(sessionEstablisher.establish(request, response, visitor.getEmail()).getUser());
        demo.seed(visitor.getId());
        return user;
    }
}
