package com.mecfin.bankprovider.application;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Um lançamento reportado pelo banco. amount com sinal (negativo = saída), convenção das contas
 * bancárias no provedor. posted = liquidado; pendente pode mudar de id ao liquidar, por isso a
 * sincronização só importa os liquidados.
 */
public record ExternalBankTransaction(
        String externalTransactionId,
        String externalAccountId,
        BigDecimal amount,
        String description,
        LocalDate date,
        boolean posted) {
}
