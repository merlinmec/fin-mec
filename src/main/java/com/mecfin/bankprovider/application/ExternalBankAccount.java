package com.mecfin.bankprovider.application;

// Retorno de BankProviderClient.getAccounts() - uma conta do banco de origem, ainda não
// mapeada para um Account do produto (esse mapeamento é decisão de quem consome a porta, não
// dela).
public record ExternalBankAccount(String externalAccountId, String name, String type, String currency) {
}
