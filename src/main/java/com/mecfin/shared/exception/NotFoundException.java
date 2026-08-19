package com.mecfin.shared.exception;

/**
 * Base para recurso inexistente OU não pertencente ao household do usuário
 * autenticado — as duas situações retornam a mesma resposta de propósito
 * (mitigação de IDOR/BOLA: não revelar se um id de outro household existe).
 * Mapeada genericamente para 404 pelo {@code GlobalExceptionHandler}.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
