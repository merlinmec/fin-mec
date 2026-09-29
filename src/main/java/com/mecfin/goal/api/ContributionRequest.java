package com.mecfin.goal.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

// amount > 0 = aporte, < 0 = resgate. date omitida = hoje.
public record ContributionRequest(@NotNull BigDecimal amount, LocalDate date, @Size(max = 255) String note) {
}
