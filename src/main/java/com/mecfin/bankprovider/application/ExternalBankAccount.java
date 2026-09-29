package com.mecfin.bankprovider.application;

import java.math.BigDecimal;

// Uma conta do banco de origem. savings = poupança (vira AccountType.SAVINGS ao criar a conta
// no produto); balance é o saldo reportado pelo banco.
public record ExternalBankAccount(String externalAccountId, String name, String number, boolean savings,
        BigDecimal balance, String currency) {
}
