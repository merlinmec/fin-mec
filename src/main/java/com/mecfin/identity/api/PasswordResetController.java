package com.mecfin.identity.api;

import com.mecfin.identity.application.PasswordResetService;
import com.mecfin.identity.application.RateLimitExceededException;
import com.mecfin.identity.infra.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Recuperação de senha por e-mail (rotas públicas). */
@RestController
@RequestMapping("/auth/password")
public class PasswordResetController {

    public record ForgotPasswordRequest(@NotBlank @Email @Size(max = 255) String email) {
    }

    public record ResetPasswordRequest(@NotBlank @Size(max = 100) String token,
            @NotBlank @Size(min = 10, max = 72) String newPassword) {
    }

    private final PasswordResetService passwordResetService;
    private final RateLimiter rateLimiter;

    public PasswordResetController(PasswordResetService passwordResetService, RateLimiter rateLimiter) {
        this.passwordResetService = passwordResetService;
        this.rateLimiter = rateLimiter;
    }

    /**
     * Sempre 202, exista a conta ou não — a mensagem da tela é "se houver uma conta com este
     * e-mail, enviamos um link". Limitado por IP e por e-mail para não virar canhão de spam.
     */
    @PostMapping("/forgot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void forgot(@Valid @RequestBody ForgotPasswordRequest request, HttpServletRequest httpRequest) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (!rateLimiter.tryConsume("forgot-ip:" + httpRequest.getRemoteAddr(), 10, Duration.ofHours(1))
                || !rateLimiter.tryConsume("forgot-email:" + email, 3, Duration.ofHours(1))) {
            throw new RateLimitExceededException();
        }
        passwordResetService.requestReset(email, AuthController.client(httpRequest));
    }

    @PostMapping("/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@Valid @RequestBody ResetPasswordRequest request, HttpServletRequest httpRequest) {
        if (!rateLimiter.tryConsume("reset-ip:" + httpRequest.getRemoteAddr(), 10, Duration.ofMinutes(10))) {
            throw new RateLimitExceededException();
        }
        passwordResetService.resetPassword(request.token(), request.newPassword(), AuthController.client(httpRequest));
    }
}
