package com.mecfin.creditcard.application;

import com.mecfin.shared.exception.NotFoundException;
import java.util.UUID;

public class CreditCardInvoiceNotFoundException extends NotFoundException {

    public CreditCardInvoiceNotFoundException(UUID id) {
        super("Fatura não encontrada: " + id);
    }
}
