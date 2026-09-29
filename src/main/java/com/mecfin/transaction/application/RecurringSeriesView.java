package com.mecfin.transaction.application;

import com.mecfin.transaction.domain.RecurringSeries;
import java.time.LocalDate;

// nextPendingDate é derivado (primeira ocorrência ainda não efetivada), nunca persistido.
public record RecurringSeriesView(RecurringSeries series, LocalDate nextPendingDate) {
}
