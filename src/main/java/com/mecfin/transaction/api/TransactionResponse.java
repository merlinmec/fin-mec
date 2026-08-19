package com.mecfin.transaction.api;

import com.mecfin.transaction.domain.Transaction;
import com.mecfin.transaction.domain.TransactionStatus;
import com.mecfin.transaction.domain.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        UUID accountId,
        UUID categoryId,
        TransactionType type,
        BigDecimal amount,
        String description,
        LocalDate transactionDate,
        YearMonth competenceMonth,
        TransactionStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getAccountId(),
                transaction.getCategoryId(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getDescription(),
                transaction.getTransactionDate(),
                transaction.getCompetenceMonth(),
                transaction.getStatus(),
                transaction.getCreatedAt(),
                transaction.getUpdatedAt());
    }
}
