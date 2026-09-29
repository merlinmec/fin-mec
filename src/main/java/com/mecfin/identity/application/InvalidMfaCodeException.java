package com.mecfin.identity.application;

import com.mecfin.shared.exception.UnauthorizedException;

public class InvalidMfaCodeException extends UnauthorizedException {

    public InvalidMfaCodeException() {
        super("Código de verificação inválido ou expirado");
    }
}
