package com.mecfin.goal.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

// amount > 0 = aporte, < 0 = resgate. date omitida = hoje. accountId = conta de onde sai o aporte
// (ou para onde volta o resgate) - com ele, a transferência entre contas é criada junto.
public record ContributionRequest(@NotNull BigDecimal amount, LocalDate date, @Size(max = 255) String note,
        UUID accountId) {
}
