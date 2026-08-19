package com.mecfin.creditcard.api;

import com.mecfin.creditcard.domain.CreditCard;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CreditCardResponse(
        UUID id,
        String name,
        BigDecimal creditLimit,
        int closingDay,
        int dueDay,
        UUID paymentAccountId,
        boolean archived,
        Instant createdAt,
        Instant updatedAt) {

    public static CreditCardResponse from(CreditCard card) {
        return new CreditCardResponse(
                card.getId(), card.getName(), card.getCreditLimit(), card.getClosingDay(), card.getDueDay(),
                card.getPaymentAccountId(), card.isArchived(), card.getCreatedAt(), card.getUpdatedAt());
    }
}
