package com.mecfin.identity.application;

import com.mecfin.shared.exception.ConflictException;

public class DuplicateEmailException extends ConflictException {

    public DuplicateEmailException(String email) {
        super("E-mail já cadastrado: " + email);
    }
}
