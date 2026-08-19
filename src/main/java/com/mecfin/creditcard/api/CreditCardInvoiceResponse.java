package com.mecfin.creditcard.api;

import com.mecfin.creditcard.application.CreditCardInvoiceView;
import com.mecfin.creditcard.domain.CreditCardInvoiceStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

public record CreditCardInvoiceResponse(
        UUID id,
        UUID creditCardId,
        YearMonth referenceMonth,
        LocalDate closingDate,
        LocalDate dueDate,
        CreditCardInvoiceStatus status,
        UUID paidTransactionId,
        BigDecimal totalAmount,
        List<CreditCardChargeResponse> charges) {

    // status aqui é o efetivo (view.effectiveStatus(), pode ser CLOSED calculado) - nunca o
    // valor bruto persistido em invoice.getStatus(). totalAmount é sempre derivado.
    public static CreditCardInvoiceResponse from(CreditCardInvoiceView view) {
        return new CreditCardInvoiceResponse(
                view.invoice().getId(),
                view.invoice().getCreditCardId(),
                view.invoice().getReferenceMonth(),
                view.invoice().getClosingDate(),
                view.invoice().getDueDate(),
                view.effectiveStatus(),
                view.invoice().getPaidTransactionId(),
                view.totalAmount(),
                view.charges().stream().map(CreditCardChargeResponse::from).toList());
    }
}
