package com.mecfin.creditcard.application;

import com.mecfin.shared.exception.NotFoundException;
import java.util.UUID;

public class CreditCardChargeNotFoundException extends NotFoundException {

    public CreditCardChargeNotFoundException(UUID id) {
        super("Cobrança não encontrada: " + id);
    }
}
