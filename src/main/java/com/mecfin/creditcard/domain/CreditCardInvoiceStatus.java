package com.mecfin.creditcard.domain;

/**
 * {@code OPEN}/{@code PAID} são os únicos valores persistidos. {@code CLOSED} é sempre
 * calculado na leitura (ver {@code CreditCardInvoiceView#effectiveStatus}) quando a fatura
 * ainda está OPEN mas já passou da data de fechamento - mesmo padrão de
 * {@code BillStatus.OVERDUE}, nunca gravado no banco.
 */
public enum CreditCardInvoiceStatus {
    OPEN,
    CLOSED,
    PAID
}
