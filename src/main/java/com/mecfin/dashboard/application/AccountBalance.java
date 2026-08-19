package com.mecfin.dashboard.application;

import com.mecfin.account.domain.AccountType;
import java.math.BigDecimal;
import java.util.UUID;

// ledgerBalance (saldo contábil) = initialBalance + todo lançamento POSTED, qualquer data.
// availableBalance (saldo disponível) = idem, mas só lançamentos POSTED com transactionDate
// até hoje - evita que um lançamento futuro já POSTED infle "quanto eu tenho agora".
public record AccountBalance(
        UUID accountId, String accountName, AccountType accountType, BigDecimal ledgerBalance, BigDecimal availableBalance) {
}
