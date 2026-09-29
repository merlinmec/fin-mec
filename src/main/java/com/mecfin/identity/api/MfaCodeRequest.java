package com.mecfin.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record MfaCodeRequest(@NotBlank @Pattern(regexp = "\\d{6}", message = "código deve ter 6 dígitos") String code) {
}
