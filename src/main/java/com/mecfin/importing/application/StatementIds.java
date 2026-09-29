package com.mecfin.importing.application;

import com.mecfin.importing.domain.DescriptionNormalizer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;

/**
 * Identificador determinístico para linha de extrato sem ID próprio (CSV). occurrence
 * diferencia duas compras idênticas no mesmo dia dentro do mesmo arquivo (dois cafés de
 * R$ 7,00): a 1ª e a 2ª ocorrência geram IDs diferentes, e reimportar o arquivo gera os mesmos.
 */
final class StatementIds {

    private StatementIds() {
    }

    static String hash(LocalDate date, BigDecimal amount, String description, int occurrence) {
        String key = date + "|" + amount.stripTrailingZeros().toPlainString() + "|"
                + DescriptionNormalizer.normalize(description) + "|" + occurrence;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return "csv:" + HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
