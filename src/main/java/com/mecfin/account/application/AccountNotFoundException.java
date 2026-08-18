package com.mecfin.account.application;

import com.mecfin.shared.exception.NotFoundException;
import java.util.UUID;

public class AccountNotFoundException extends NotFoundException {

    public AccountNotFoundException(UUID id) {
        super("Conta não encontrada: " + id);
    }
}
