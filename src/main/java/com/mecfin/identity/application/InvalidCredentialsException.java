package com.mecfin.identity.application;

import com.mecfin.shared.exception.UnauthorizedException;

public class InvalidCredentialsException extends UnauthorizedException {

    public InvalidCredentialsException() {
        super("E-mail ou senha inválidos");
    }
}
