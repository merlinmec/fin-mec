package com.mecfin.identity.api;

import com.mecfin.identity.application.AuthService;
import com.mecfin.identity.application.ClientInfo;
import com.mecfin.identity.application.InvalidMfaCodeException;
import com.mecfin.identity.application.MfaService;
import com.mecfin.identity.application.RateLimitExceededException;
import com.mecfin.identity.domain.AuthenticatedUser;
import com.mecfin.identity.domain.User;
import com.mecfin.identity.infra.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Duration MFA_CHALLENGE_TTL = Duration.ofMinutes(5);

    private final AuthService authService;
    private final MfaService mfaService;
    private final SessionEstablisher sessionEstablisher;
    private final RateLimiter rateLimiter;
    private final Clock clock;
    private final int registerCapacityPerMinute;
    private final int loginCapacityPerMinute;

    public AuthController(AuthService authService, MfaService mfaService, SessionEstablisher sessionEstablisher,
            RateLimiter rateLimiter, Clock clock,
            @Value("${mecfin.rate-limit.register-per-minute:3}") int registerCapacityPerMinute,
            @Value("${mecfin.rate-limit.login-per-minute:5}") int loginCapacityPerMinute) {
        this.authService = authService;
        this.mfaService = mfaService;
        this.sessionEstablisher = sessionEstablisher;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
        this.registerCapacityPerMinute = registerCapacityPerMinute;
        this.loginCapacityPerMinute = loginCapacityPerMinute;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        String key = "register:" + httpRequest.getRemoteAddr();
        if (!rateLimiter.tryConsume(key, registerCapacityPerMinute, Duration.ofMinutes(1))) {
            throw new RateLimitExceededException();
        }
        User user = authService.register(request.email(), request.password());
        authService.registerSuccess(user.getId(), client(httpRequest));
        return UserResponse.from(sessionEstablisher.establish(httpRequest, httpResponse, user.getEmail()).getUser());
    }

    /**
     * Primeiro passo do login. Sem 2FA: 200 com o usuário e sessão autenticada. Com 2FA: 202
     * {@code {"mfaRequired": true}} e a sessão guarda só o desafio pendente (5 min) - nada
     * autenticado até POST /auth/login/mfa.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        String key = "login:" + httpRequest.getRemoteAddr() + "|" + normalizedEmail;
        if (!rateLimiter.tryConsume(key, loginCapacityPerMinute, Duration.ofMinutes(1))) {
            throw new RateLimitExceededException();
        }
        ClientInfo client = client(httpRequest);
        User user = authService.verifyPassword(normalizedEmail, request.password(), client);
        if (user.isTotpEnabled()) {
            HttpSession session = httpRequest.getSession(true);
            httpRequest.changeSessionId();
            session.setAttribute(PendingMfaLogin.SESSION_ATTRIBUTE,
                    new PendingMfaLogin(user.getId(), user.getEmail(), clock.instant().plus(MFA_CHALLENGE_TTL), 0));
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(new LoginResult(true));
        }
        authService.registerSuccess(user.getId(), client);
        return ResponseEntity.ok(UserResponse.from(
                sessionEstablisher.establish(httpRequest, httpResponse, user.getEmail()).getUser()));
    }

    /**
     * Segundo passo do login com 2FA. Cada código errado conta para o bloqueio da conta e o
     * desafio morre após {@value PendingMfaLogin#MAX_ATTEMPTS} erros - adivinhar 6 dígitos
     * exigiria refazer o login com senha inúmeras vezes, e o bloqueio para isso antes.
     */
    @PostMapping("/login/mfa")
    public UserResponse loginMfa(@Valid @RequestBody MfaLoginRequest request, HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        HttpSession session = httpRequest.getSession(false);
        PendingMfaLogin pending = session == null
                ? null
                : (PendingMfaLogin) session.getAttribute(PendingMfaLogin.SESSION_ATTRIBUTE);
        if (pending == null || pending.expiresAt().isBefore(clock.instant())
                || pending.attempts() >= PendingMfaLogin.MAX_ATTEMPTS || authService.isLocked(pending.userId())) {
            if (session != null) {
                session.removeAttribute(PendingMfaLogin.SESSION_ATTRIBUTE);
            }
            throw new InvalidMfaCodeException();
        }
        ClientInfo client = client(httpRequest);
        if (!mfaService.verifySecondFactor(pending.userId(), request.code(), client)) {
            session.setAttribute(PendingMfaLogin.SESSION_ATTRIBUTE, pending.withAttempt());
            authService.registerSecondFactorFailure(pending.userId(), client);
            throw new InvalidMfaCodeException();
        }
        session.removeAttribute(PendingMfaLogin.SESSION_ATTRIBUTE);
        authService.registerSuccess(pending.userId(), client);
        return UserResponse.from(sessionEstablisher.establish(httpRequest, httpResponse, pending.email()).getUser());
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return UserResponse.from(principal.getUser());
    }

    static ClientInfo client(HttpServletRequest request) {
        return new ClientInfo(request.getRemoteAddr(), request.getHeader("User-Agent"));
    }
}
