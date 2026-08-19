package com.mecfin.identity.api;

import com.mecfin.identity.application.AuthService;
import com.mecfin.identity.application.InvalidCredentialsException;
import com.mecfin.identity.application.RateLimitExceededException;
import com.mecfin.identity.domain.AuthenticatedUser;
import com.mecfin.identity.domain.User;
import com.mecfin.identity.infra.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final RateLimiter rateLimiter;
    private final int registerCapacityPerMinute;
    private final int loginCapacityPerMinute;
    private final SecurityContextHolderStrategy securityContextHolderStrategy = SecurityContextHolder.getContextHolderStrategy();

    public AuthController(AuthService authService, AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository, RateLimiter rateLimiter,
            @Value("${mecfin.rate-limit.register-per-minute:3}") int registerCapacityPerMinute,
            @Value("${mecfin.rate-limit.login-per-minute:5}") int loginCapacityPerMinute) {
        this.authService = authService;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.rateLimiter = rateLimiter;
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
        return authenticateAndCreateSession(httpRequest, httpResponse, user.getEmail(), request.password());
    }

    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        String key = "login:" + httpRequest.getRemoteAddr() + "|" + normalizedEmail;
        if (!rateLimiter.tryConsume(key, loginCapacityPerMinute, Duration.ofMinutes(1))) {
            throw new RateLimitExceededException();
        }
        return authenticateAndCreateSession(httpRequest, httpResponse, normalizedEmail, request.password());
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return UserResponse.from(principal.getUser());
    }

    private UserResponse authenticateAndCreateSession(HttpServletRequest request, HttpServletResponse response,
            String email, String rawPassword) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(email, rawPassword));
        } catch (AuthenticationException ex) {
            throw new InvalidCredentialsException();
        }

        SecurityContext context = securityContextHolderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        securityContextHolderStrategy.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        AuthenticatedUser principal = (AuthenticatedUser) authentication.getPrincipal();
        return UserResponse.from(principal.getUser());
    }
}
