package com.mecfin.creditcard.application;

import com.mecfin.shared.exception.NotFoundException;
import java.util.UUID;

public class CreditCardNotFoundException extends NotFoundException {

    public CreditCardNotFoundException(UUID id) {
        super("Cartão de crédito não encontrado: " + id);
    }
}
