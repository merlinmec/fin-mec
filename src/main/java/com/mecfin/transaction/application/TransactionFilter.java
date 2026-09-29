package com.mecfin.transaction.application;

import com.mecfin.transaction.domain.TransactionStatus;
import com.mecfin.transaction.domain.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Locale;
import java.util.UUID;

/**
 * Filtros combináveis da listagem de lançamentos (e da exportação CSV, que usa exatamente os
 * mesmos - o arquivo exportado é sempre "o que está na tela"). Todo campo é opcional.
 *
 * @param query      texto livre, casado por "contém" na descrição, sem diferenciar maiúsculas
 * @param from       data do lançamento a partir de (inclusive)
 * @param to         data do lançamento até (inclusive)
 */
public record TransactionFilter(
        UUID accountId,
        UUID categoryId,
        TransactionType type,
        TransactionStatus status,
        YearMonth competenceMonth,
        LocalDate from,
        LocalDate to,
        String query,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        UUID tagId) {

    public static final int MAX_QUERY_LENGTH = 100;

    public TransactionFilter {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("'from' não pode ser posterior a 'to'");
        }
        if (minAmount != null && maxAmount != null && minAmount.compareTo(maxAmount) > 0) {
            throw new IllegalArgumentException("minAmount não pode ser maior que maxAmount");
        }
        if (query != null) {
            query = query.strip();
            if (query.isEmpty()) {
                query = null;
            } else if (query.length() > MAX_QUERY_LENGTH) {
                throw new IllegalArgumentException("q aceita no máximo " + MAX_QUERY_LENGTH + " caracteres");
            }
        }
    }

    public static TransactionFilter empty() {
        return new TransactionFilter(null, null, null, null, null, null, null, null, null, null, null);
    }

    /**
     * Padrão LIKE do texto livre: minúsculo, com os curingas do próprio usuário escapados por
     * "!" ({@code 50%} procura literalmente "50%", não "50 seguido de qualquer coisa") - ver
     * TransactionRepository.SEARCH_PREDICATE.
     */
    public String likePattern() {
        if (query == null) {
            return null;
        }
        String escaped = query.toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }
}
