package com.mecfin.dashboard.api;

import com.mecfin.account.domain.AccountType;
import com.mecfin.dashboard.application.AccountBalance;
import java.math.BigDecimal;
import java.util.UUID;

public record AccountBalanceResponse(
        UUID accountId, String accountName, AccountType accountType, BigDecimal ledgerBalance, BigDecimal availableBalance) {

    public static AccountBalanceResponse from(AccountBalance balance) {
        return new AccountBalanceResponse(
                balance.accountId(), balance.accountName(), balance.accountType(), balance.ledgerBalance(),
                balance.availableBalance());
    }
}
