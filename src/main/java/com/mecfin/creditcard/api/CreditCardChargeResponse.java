package com.mecfin.creditcard.api;

import com.mecfin.creditcard.domain.CreditCardCharge;
import com.mecfin.transaction.domain.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreditCardChargeResponse(
        UUID id,
        UUID creditCardInvoiceId,
        UUID categoryId,
        TransactionType type,
        BigDecimal amount,
        String description,
        LocalDate purchaseDate,
        Integer installmentNumber,
        Integer installmentTotal) {

    public static CreditCardChargeResponse from(CreditCardCharge charge) {
        return new CreditCardChargeResponse(
                charge.getId(), charge.getCreditCardInvoiceId(), charge.getCategoryId(), charge.getType(),
                charge.getAmount(), charge.getDescription(), charge.getPurchaseDate(),
                charge.getInstallmentNumber(), charge.getInstallmentTotal());
    }
}
