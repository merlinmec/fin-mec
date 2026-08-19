package com.mecfin.notification.domain;

// O que sourceId referencia (Bill.id ou CreditCardInvoice.id, sempre por id solto - nunca
// relacao JPA, mesmo padrao do resto do projeto). Persistido explicitamente em vez de inferido
// do prefixo de NotificationType, pra o cliente nao precisar fazer parsing de string.
public enum NotificationSourceType {
    BILL,
    CREDIT_CARD_INVOICE
}
