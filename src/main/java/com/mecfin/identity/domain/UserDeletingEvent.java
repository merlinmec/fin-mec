package com.mecfin.identity.domain;

import java.util.UUID;

// Publicado por AccountSecurityService.deleteAccount antes de apagar o usuário, dentro da
// mesma transação - quem guarda dado do usuário (household) apaga o seu em reação.
public record UserDeletingEvent(UUID userId) {
}
