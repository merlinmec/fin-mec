package com.mecfin.creditcard.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

// accountId é opcional só quando o cartão já tem paymentAccountId padrão (CreditCardService.pay
// valida isso). paidAmount opcional - null usa o total derivado das cobranças (pagamento sem
// juros/desconto). Mesmo espírito de PayBillRequest.
public record PayCreditCardInvoiceRequest(UUID accountId, @NotNull LocalDate paymentDate, @Positive BigDecimal paidAmount) {
}
