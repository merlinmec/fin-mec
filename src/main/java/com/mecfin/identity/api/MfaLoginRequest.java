package com.mecfin.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// code = 6 dígitos do app autenticador OU um código de recuperação (XXXXX-XXXXX).
public record MfaLoginRequest(@NotBlank @Size(max = 20) String code) {
}
