package com.mecfin.creditcard.infra;

import com.mecfin.creditcard.domain.CreditCardInvoice;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditCardInvoiceRepository extends JpaRepository<CreditCardInvoice, UUID> {

    // Usado por CreditCardService.resolveOrCreateInvoice para achar (ou não) a fatura já
    // aberta pra um mês antes de criar uma nova - referenceMonth único por cartão, ver
    // uq_credit_card_invoices_card_month.
    Optional<CreditCardInvoice> findByCreditCardIdAndReferenceMonth(UUID creditCardId, LocalDate referenceMonth);

    List<CreditCardInvoice> findAllByCreditCardIdOrderByReferenceMonthDesc(UUID creditCardId);

    Optional<CreditCardInvoice> findByIdAndCreditCardIdIn(UUID id, List<UUID> creditCardIds);
}
