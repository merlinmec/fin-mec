package com.mecfin.bill.api;

import com.mecfin.shared.domain.RecurrenceRule;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record UpdateBillRequest(
        @NotBlank @Size(max = 255) String description,
        @NotNull @Positive BigDecimal amount,
        @NotNull LocalDate dueDate,
        UUID sourceAccountId,
        UUID categoryId,
        RecurrenceRule recurrenceRule) {
}
