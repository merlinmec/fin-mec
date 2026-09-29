package com.mecfin.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class RecurrenceRuleTest {

    @Test
    void monthlyFromDay31ClampsToMonthEndAndComesBack() {
        LocalDate start = LocalDate.of(2026, 1, 31);

        assertThat(RecurrenceRule.MONTHLY.occurrence(start, 1)).isEqualTo(LocalDate.of(2026, 2, 28));
        // Calculado a partir da data inicial, não da ocorrência anterior: volta para o dia 31.
        assertThat(RecurrenceRule.MONTHLY.occurrence(start, 2)).isEqualTo(LocalDate.of(2026, 3, 31));
    }

    @Test
    void indexZeroIsTheStartDateForEveryRule() {
        LocalDate start = LocalDate.of(2026, 5, 10);
        for (RecurrenceRule rule : RecurrenceRule.values()) {
            assertThat(rule.occurrence(start, 0)).isEqualTo(start);
        }
    }

    @Test
    void stepsMatchEachPeriodicity() {
        LocalDate start = LocalDate.of(2026, 5, 10);

        assertThat(RecurrenceRule.WEEKLY.occurrence(start, 2)).isEqualTo(LocalDate.of(2026, 5, 24));
        assertThat(RecurrenceRule.BIWEEKLY.occurrence(start, 1)).isEqualTo(LocalDate.of(2026, 5, 24));
        assertThat(RecurrenceRule.BIMONTHLY.occurrence(start, 1)).isEqualTo(LocalDate.of(2026, 7, 10));
        assertThat(RecurrenceRule.TRIMONTHLY.occurrence(start, 1)).isEqualTo(LocalDate.of(2026, 8, 10));
        assertThat(RecurrenceRule.YEARLY.occurrence(start, 1)).isEqualTo(LocalDate.of(2027, 5, 10));
    }
}
