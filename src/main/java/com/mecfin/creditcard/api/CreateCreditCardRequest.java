package com.mecfin.creditcard.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateCreditCardRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull @Positive BigDecimal creditLimit,
        @NotNull @Min(1) @Max(31) Integer closingDay,
        @NotNull @Min(1) @Max(31) Integer dueDay,
        UUID paymentAccountId) {
}
