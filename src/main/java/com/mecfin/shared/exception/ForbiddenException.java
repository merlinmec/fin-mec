package com.mecfin.shared.exception;

// Autenticado, mas sem permissão para a ação (ex.: membro tentando o que só o dono pode): 403.
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
