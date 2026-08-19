package com.mecfin.creditcard.application;

import com.mecfin.creditcard.domain.CreditCardCharge;
import com.mecfin.creditcard.domain.CreditCardInvoice;
import com.mecfin.creditcard.domain.CreditCardInvoiceStatus;
import com.mecfin.transaction.domain.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Fatura + total (derivado, somando as cobranças - EXPENSE soma, INCOME/estorno subtrai) e
 * status efetivo calculado na leitura (nunca persistidos, ver {@link CreditCardInvoice}).
 * Modelo de leitura composto, não entidade JPA - mesmo padrão de {@code BudgetView}/{@code BillView}.
 */
public record CreditCardInvoiceView(CreditCardInvoice invoice, List<CreditCardCharge> charges) {

    public BigDecimal totalAmount() {
        return charges.stream()
                .map(charge -> charge.getType() == TransactionType.EXPENSE
                        ? charge.getAmount()
                        : charge.getAmount().negate())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public CreditCardInvoiceStatus effectiveStatus() {
        if (invoice.getStatus() == CreditCardInvoiceStatus.OPEN && invoice.getClosingDate().isBefore(LocalDate.now())) {
            return CreditCardInvoiceStatus.CLOSED;
        }
        return invoice.getStatus();
    }
}
