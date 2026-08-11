package com.mecfin.shared.exception;

/**
 * Base para falhas de autenticação de aplicação (ex.: credenciais inválidas),
 * distinta da entrada não-autenticada tratada pelo {@code AuthenticationEntryPoint}
 * da security filter chain. Mapeada genericamente para 401 pelo
 * {@code GlobalExceptionHandler}.
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
