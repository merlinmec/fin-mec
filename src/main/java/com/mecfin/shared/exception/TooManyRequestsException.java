package com.mecfin.shared.exception;

/**
 * Base para limites de taxa excedidos. Mapeada genericamente para 429 pelo
 * {@code GlobalExceptionHandler}.
 */
public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException(String message) {
        super(message);
    }
}
