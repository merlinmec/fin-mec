package com.mecfin.identity.domain;

import java.util.UUID;

/**
 * Publicado por {@code AuthService.register()} ao final do registro. Consumido
 * pelo módulo {@code household}, que cria o household pessoal do novo usuário —
 * o módulo identity não conhece household, só anuncia o fato de domínio.
 */
public record UserRegisteredEvent(UUID userId, String email) {
}
