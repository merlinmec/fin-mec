package com.mecfin.budget.api;

import com.mecfin.budget.application.BudgetView;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

public record BudgetResponse(
        UUID id,
        UUID categoryId,
        YearMonth referenceMonth,
        BigDecimal amount,
        BigDecimal spent,
        BigDecimal percentageUsed,
        Instant createdAt,
        Instant updatedAt) {

    public static BudgetResponse from(BudgetView view) {
        return new BudgetResponse(
                view.budget().getId(),
                view.budget().getCategoryId(),
                view.budget().getReferenceMonth(),
                view.budget().getAmount(),
                view.spent(),
                view.percentageUsed(),
                view.budget().getCreatedAt(),
                view.budget().getUpdatedAt());
    }
}
