package com.mecfin.bankprovider.domain;

/**
 * {@code ACTIVE} logo após a conexão ser criada. {@code ERROR}/{@code EXPIRED} refletem falha
 * de sincronização ou token expirado reportados pelo adapter concreto (o usuário precisa
 * reconectar - ver seção H do doc de arquitetura). {@code DISCONNECTED} é definitivo, o
 * usuário removeu a conexão. Todos os quatro são persistidos (diferente de
 * {@code BillStatus}/{@code CreditCardInvoiceStatus}, aqui não há estado calculado na leitura).
 */
public enum BankConnectionStatus {
    ACTIVE,
    ERROR,
    EXPIRED,
    DISCONNECTED
}
