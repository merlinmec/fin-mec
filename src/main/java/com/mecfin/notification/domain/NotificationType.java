package com.mecfin.notification.domain;

// DUE_SOON e OVERDUE existem em pares por origem (BILL_*/CREDIT_CARD_INVOICE_*) porque cada
// mudanca de um pro outro e informacao nova de verdade (o unique constraint da tabela permite
// as duas coexistirem, ver migration) - diferente de BillStatus.OVERDUE/CreditCardInvoiceStatus
// .CLOSED, aqui NAO ha estado calculado na leitura: uma vez criada, a notificacao fica gravada
// ate o usuario marcar como lida.
public enum NotificationType {
    BILL_DUE_SOON,
    BILL_OVERDUE,
    CREDIT_CARD_INVOICE_DUE_SOON,
    CREDIT_CARD_INVOICE_OVERDUE
}
