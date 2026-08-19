package com.mecfin.bankprovider.application;

import java.math.BigDecimal;
import java.time.Instant;

// Retorno de BankProviderClient.getBalances() - saldo reportado pelo banco de origem numa
// conta externa, num instante (asOf) - não confundir com o saldo contábil/disponível
// calculado por DashboardService a partir de Transaction.
public record ExternalBalance(String externalAccountId, BigDecimal amount, Instant asOf) {
}
