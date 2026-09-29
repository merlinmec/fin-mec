package com.mecfin.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Confirmação para operação sensível: senha sempre, código só quando o 2FA está ativo.
public record ReauthenticationRequest(@NotBlank String password, @Size(max = 20) String code) {
}
