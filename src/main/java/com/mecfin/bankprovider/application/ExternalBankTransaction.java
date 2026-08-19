package com.mecfin.bankprovider.application;

import java.math.BigDecimal;
import java.time.LocalDate;

// Retorno de BankProviderClient.getTransactions() - um lançamento reportado pelo banco de
// origem, ainda não convertido em Transaction do produto. externalTransactionId é a chave de
// idempotência prevista no doc de arquitetura (UNIQUE(bank_connection_id, external_id) em
// transactions) - quem consome a porta usa esse id pra nunca duplicar ao reprocessar um sync.
public record ExternalBankTransaction(
        String externalTransactionId,
        String externalAccountId,
        BigDecimal amount,
        String description,
        LocalDate date) {
}
