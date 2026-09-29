package com.mecfin.bankprovider.application;

import com.mecfin.bankprovider.domain.BankAccountLinkMode;
import com.mecfin.bankprovider.domain.BankConnectionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BankConnectionView(
        UUID id,
        String institutionName,
        String institutionImageUrl,
        String institutionColor,
        BankConnectionStatus status,
        String lastError,
        Instant lastSyncedAt,
        Instant createdAt,
        List<AccountLink> accounts) {

    public record AccountLink(UUID id, String name, String number, BigDecimal bankBalance, Instant bankBalanceAt,
            BankAccountLinkMode mode, UUID accountId) {
    }
}
