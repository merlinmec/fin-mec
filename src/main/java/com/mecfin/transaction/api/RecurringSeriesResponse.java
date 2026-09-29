package com.mecfin.transaction.api;

import com.mecfin.shared.domain.RecurrenceRule;
import com.mecfin.transaction.application.RecurringSeriesView;
import com.mecfin.transaction.domain.RecurringSeries;
import com.mecfin.transaction.domain.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RecurringSeriesResponse(
        UUID id,
        UUID accountId,
        UUID categoryId,
        TransactionType type,
        BigDecimal amount,
        String description,
        RecurrenceRule recurrenceRule,
        LocalDate startDate,
        LocalDate endDate,
        boolean active,
        LocalDate nextPendingDate) {

    public static RecurringSeriesResponse from(RecurringSeriesView view) {
        RecurringSeries series = view.series();
        return new RecurringSeriesResponse(
                series.getId(),
                series.getAccountId(),
                series.getCategoryId(),
                series.getType(),
                series.getAmount(),
                series.getDescription(),
                series.getRecurrenceRule(),
                series.getStartDate(),
                series.getEndDate(),
                series.isActive(),
                view.nextPendingDate());
    }
}
