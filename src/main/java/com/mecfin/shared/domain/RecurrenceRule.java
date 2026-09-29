package com.mecfin.shared.domain;

import java.time.LocalDate;

// Periodicidade de um lançamento fixo (Transaction, via RecurringSeries) ou de uma conta a pagar
// recorrente (Bill). Até a Fase 10 era só metadado; a partir da Fase 11 (decisão do usuário,
// 28/09/2026) o motor de recorrência gera as ocorrências de verdade - ver RecurringSeriesService.
// Vive em shared.domain porque é vocabulário compartilhado entre Transaction e Bill.
public enum RecurrenceRule {
    WEEKLY,
    BIWEEKLY,
    MONTHLY,
    BIMONTHLY,
    TRIMONTHLY,
    YEARLY;

    /**
     * Data da ocorrência de índice {@code index} (0 = a própria {@code start}). Sempre calculada
     * a partir da data inicial, nunca da ocorrência anterior: assim uma série mensal que começa
     * dia 31 cai no último dia de fevereiro e volta para 31 em março, em vez de "grudar" no 28.
     */
    public LocalDate occurrence(LocalDate start, int index) {
        return switch (this) {
            case WEEKLY -> start.plusWeeks(index);
            case BIWEEKLY -> start.plusWeeks(2L * index);
            case MONTHLY -> start.plusMonths(index);
            case BIMONTHLY -> start.plusMonths(2L * index);
            case TRIMONTHLY -> start.plusMonths(3L * index);
            case YEARLY -> start.plusYears(index);
        };
    }
}
