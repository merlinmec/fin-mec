package com.mecfin.transaction.domain;

// Só preenchido quando type == TRANSFER (null nos demais) - indica se esta perna tira
// dinheiro da conta (OUT) ou coloca (IN), para um futuro cálculo de saldo saber o sinal
// sem precisar seguir transferPairId toda vez.
public enum TransactionDirection {
    OUT,
    IN
}
