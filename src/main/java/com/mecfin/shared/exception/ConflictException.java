package com.mecfin.shared.exception;

/**
 * Base para conflitos de estado (ex.: recurso único já existente). Mapeada
 * genericamente para 409 pelo {@code GlobalExceptionHandler}, sem que este
 * precise conhecer as subclasses específicas de cada domínio.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
