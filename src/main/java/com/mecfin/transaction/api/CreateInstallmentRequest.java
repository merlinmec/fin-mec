package com.mecfin.transaction.api;

import com.mecfin.transaction.domain.TransactionType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

// amountPerInstallment é o valor de CADA parcela (não o total) - evita ambiguidade/resto de
// divisão. Cada parcela nasce POSTED (parcelamento não tem noção de "pendente" separada).
public record CreateInstallmentRequest(
        @NotNull UUID accountId,
        UUID categoryId,
        @NotNull TransactionType type,
        @NotNull @Positive BigDecimal amountPerInstallment,
        @NotBlank @Size(max = 255) String description,
        @NotNull LocalDate firstTransactionDate,
        @NotNull YearMonth firstCompetenceMonth,
        @Min(2) int installments,
        @Size(max = 10) List<UUID> tagIds) {

    public CreateInstallmentRequest(
            UUID accountId,
            UUID categoryId,
            TransactionType type,
            BigDecimal amountPerInstallment,
            String description,
            LocalDate firstTransactionDate,
            YearMonth firstCompetenceMonth,
            int installments) {
        this(accountId, categoryId, type, amountPerInstallment, description, firstTransactionDate,
                firstCompetenceMonth, installments, null);
    }
}
