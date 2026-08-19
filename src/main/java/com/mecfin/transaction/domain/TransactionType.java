package com.mecfin.transaction.domain;

public enum TransactionType {
    INCOME,
    EXPENSE,
    // Nunca criado via POST /transactions - só via POST /transactions/transfers
    // (TransactionService garante as duas pernas pareadas, ver transferPairId).
    TRANSFER
}
