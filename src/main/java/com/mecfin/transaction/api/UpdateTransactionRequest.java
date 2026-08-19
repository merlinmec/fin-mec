package com.mecfin.transaction.api;

import com.mecfin.shared.domain.RecurrenceRule;
import com.mecfin.transaction.domain.TransactionStatus;
import com.mecfin.transaction.domain.TransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

// type continua editável aqui (INCOME/EXPENSE), mas nunca TRANSFER - TransactionService
// rejeita update() inteiro se o lançamento já é uma perna de transferência.
public record UpdateTransactionRequest(
        UUID categoryId,
        @NotNull TransactionType type,
        @NotNull @Positive BigDecimal amount,
        @NotBlank @Size(max = 255) String description,
        @NotNull LocalDate transactionDate,
        @NotNull YearMonth competenceMonth,
        @NotNull TransactionStatus status,
        RecurrenceRule recurrenceRule) {
}
