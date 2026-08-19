package com.mecfin.creditcard.infra;

import com.mecfin.creditcard.domain.CreditCardCharge;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditCardChargeRepository extends JpaRepository<CreditCardCharge, UUID> {

    List<CreditCardCharge> findAllByCreditCardInvoiceIdOrderByPurchaseDateAsc(UUID creditCardInvoiceId);

    Optional<CreditCardCharge> findByIdAndCreditCardInvoiceId(UUID id, UUID creditCardInvoiceId);
}
