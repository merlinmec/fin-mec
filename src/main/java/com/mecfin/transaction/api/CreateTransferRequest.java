package com.mecfin.transaction.api;

import com.mecfin.transaction.domain.TransactionStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

public record CreateTransferRequest(
        @NotNull UUID sourceAccountId,
        @NotNull UUID destinationAccountId,
        @NotNull @Positive BigDecimal amount,
        @NotBlank @Size(max = 255) String description,
        @NotNull LocalDate transactionDate,
        @NotNull YearMonth competenceMonth,
        TransactionStatus status) {
}
