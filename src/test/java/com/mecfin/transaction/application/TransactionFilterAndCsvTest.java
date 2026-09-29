package com.mecfin.transaction.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class TransactionFilterAndCsvTest {

    private static TransactionFilter withQuery(String q) {
        return new TransactionFilter(null, null, null, null, null, null, null, q, null, null);
    }

    @Test
    void likePatternLowercasesAndEscapesUserWildcards() {
        assertThat(withQuery("  Desconto 50%_OFF! ").likePattern()).isEqualTo("%desconto 50!%!_off!!%");
        assertThat(withQuery("   ").likePattern()).isNull();
        assertThat(withQuery(null).likePattern()).isNull();
    }

    @Test
    void rejectsInvertedRanges() {
        assertThatThrownBy(() -> new TransactionFilter(null, null, null, null, null,
                LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 1), null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TransactionFilter(null, null, null, null, null, null, null, null,
                BigDecimal.TEN, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsOversizedQuery() {
        assertThatThrownBy(() -> withQuery("x".repeat(TransactionFilter.MAX_QUERY_LENGTH + 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void csvCellNeutralizesFormulaInjectionAndQuotes() {
        assertThat(TransactionCsvExporter.cell("=HYPERLINK(\"http://evil\")"))
                .isEqualTo("\"'=HYPERLINK(\"\"http://evil\"\")\"");
        assertThat(TransactionCsvExporter.cell("+55 11")).isEqualTo("\"'+55 11\"");
        assertThat(TransactionCsvExporter.cell("@SUM(A1)")).isEqualTo("\"'@SUM(A1)\"");
        assertThat(TransactionCsvExporter.cell("Mercado; feira")).isEqualTo("\"Mercado; feira\"");
        assertThat(TransactionCsvExporter.cell(null)).isEqualTo("\"\"");
    }
}
