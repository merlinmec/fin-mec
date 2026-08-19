package com.mecfin.transaction.infra;

import java.math.BigDecimal;
import java.util.UUID;

// Projeção de com.mecfin.transaction.infra.TransactionRepository.sumSignedAmountsByAccount.
public interface AccountBalanceProjection {

    UUID getAccountId();

    BigDecimal getTotal();
}
