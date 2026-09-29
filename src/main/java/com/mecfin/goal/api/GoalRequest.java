package com.mecfin.goal.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

// Criar e editar usam o mesmo payload; archived só tem efeito na edição.
public record GoalRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull @Positive @DecimalMax("999999999999.99") BigDecimal targetAmount,
        LocalDate targetDate,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "cor deve estar no formato hexadecimal #RRGGBB") String color,
        @Size(max = 50) String icon,
        boolean archived) {
}
