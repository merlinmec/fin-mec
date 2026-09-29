package com.mecfin.importing.application;

import java.math.BigDecimal;

/**
 * Valor monetário em texto de extrato, nos formatos que aparecem na prática: "-1234.56",
 * "-1.234,56", "R$ 1.234,56", "(123,45)" (negativo contábil), "1,234.56". O separador decimal é
 * o último "." ou "," seguido de 1-2 dígitos no fim; o outro é de milhar.
 */
final class Amounts {

    private Amounts() {
    }

    static BigDecimal parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new StatementFormatException("Valor vazio");
        }
        String value = raw.strip().replace("R$", "").replace("\u00A0", "").replace(" ", "");
        boolean negative = false;
        if (value.startsWith("(") && value.endsWith(")")) {
            negative = true;
            value = value.substring(1, value.length() - 1);
        }
        if (value.endsWith("-")) {
            negative = true;
            value = value.substring(0, value.length() - 1);
        }
        if (value.startsWith("-")) {
            negative = !negative;
            value = value.substring(1);
        } else if (value.startsWith("+")) {
            value = value.substring(1);
        }
        int lastDot = value.lastIndexOf('.');
        int lastComma = value.lastIndexOf(',');
        int decimalAt = Math.max(lastDot, lastComma);
        boolean hasDecimals = decimalAt >= 0 && value.length() - decimalAt - 1 <= 2;
        String integerRaw = hasDecimals ? value.substring(0, decimalAt) : value;
        // Separador de milhar só em grupos de 3 ("1.234.567"): "12,3,4" é célula corrompida, não
        // 123,4 — aceitar isso gravaria um valor errado em silêncio.
        if (!integerRaw.matches("\\d+|\\d{1,3}([.,]\\d{3})+")) {
            throw new StatementFormatException("Valor inválido: " + raw.strip());
        }
        String integerPart = integerRaw.replace(".", "").replace(",", "");
        String normalized = hasDecimals ? integerPart + "." + value.substring(decimalAt + 1) : integerPart;
        if (!normalized.matches("\\d+(\\.\\d{1,2})?")) {
            throw new StatementFormatException("Valor inválido: " + raw.strip());
        }
        BigDecimal amount = new BigDecimal(normalized);
        return negative ? amount.negate() : amount;
    }
}
