package com.mecfin.bill.api;

import com.mecfin.bill.application.BillView;
import com.mecfin.bill.domain.BillStatus;
import com.mecfin.shared.domain.RecurrenceRule;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record BillResponse(
        UUID id,
        String description,
        BigDecimal amount,
        LocalDate dueDate,
        UUID sourceAccountId,
        UUID categoryId,
        BillStatus status,
        UUID paidTransactionId,
        RecurrenceRule recurrenceRule,
        Instant createdAt,
        Instant updatedAt) {

    // status aqui é o efetivo (view.effectiveStatus(), pode ser OVERDUE calculado) - nunca o
    // valor bruto persistido em bill.getStatus().
    public static BillResponse from(BillView view) {
        return new BillResponse(
                view.bill().getId(),
                view.bill().getDescription(),
                view.bill().getAmount(),
                view.bill().getDueDate(),
                view.bill().getSourceAccountId(),
                view.bill().getCategoryId(),
                view.effectiveStatus(),
                view.bill().getPaidTransactionId(),
                view.bill().getRecurrenceRule(),
                view.bill().getCreatedAt(),
                view.bill().getUpdatedAt());
    }
}
