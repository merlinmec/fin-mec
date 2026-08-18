package com.mecfin.account.api;

import com.mecfin.account.domain.Account;
import com.mecfin.account.domain.AccountType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String name,
        AccountType type,
        BigDecimal initialBalance,
        String currency,
        boolean archived,
        Instant createdAt,
        Instant updatedAt) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getName(),
                account.getType(),
                account.getInitialBalance(),
                account.getCurrency(),
                account.isArchived(),
                account.getCreatedAt(),
                account.getUpdatedAt());
    }
}
