package com.mecfin.importing.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Decisão do usuário sobre uma linha, vinda da pré-visualização: CREATE cria lançamento, MATCH
 * efetiva o previsto {@code matchTransactionId}, SKIP ignora. amount com sinal (negativo = saída).
 */
public record CommitRow(
        String externalId,
        LocalDate date,
        String description,
        BigDecimal amount,
        Action action,
        UUID categoryId,
        List<UUID> tagIds,
        UUID matchTransactionId) {

    public enum Action {
        CREATE,
        MATCH,
        SKIP
    }
}
