package com.mecfin.bill.application;

import com.mecfin.shared.exception.NotFoundException;
import java.util.UUID;

public class BillNotFoundException extends NotFoundException {

    public BillNotFoundException(UUID id) {
        super("Conta a pagar não encontrada: " + id);
    }
}
