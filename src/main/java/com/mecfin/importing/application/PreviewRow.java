package com.mecfin.importing.application;

import com.mecfin.transaction.domain.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Uma linha da pré-visualização. amount sem sinal + type (como no resto da API). A sugestão de
 * categoria vem de uma regra (RULE) ou do histórico de lançamentos com a mesma descrição
 * (HISTORY); o match traz o previsto que a linha efetivaria.
 */
public record PreviewRow(
        String externalId,
        LocalDate date,
        String description,
        BigDecimal amount,
        TransactionType type,
        PreviewStatus status,
        UUID suggestedCategoryId,
        UUID suggestedTagId,
        String suggestionSource,
        UUID matchTransactionId,
        String matchDescription,
        LocalDate matchDate,
        BigDecimal matchAmount) {
}
