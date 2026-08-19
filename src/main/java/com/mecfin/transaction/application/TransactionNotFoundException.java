package com.mecfin.transaction.application;

import com.mecfin.shared.exception.NotFoundException;
import java.util.UUID;

public class TransactionNotFoundException extends NotFoundException {

    public TransactionNotFoundException(UUID id) {
        super("Lançamento não encontrado: " + id);
    }
}
