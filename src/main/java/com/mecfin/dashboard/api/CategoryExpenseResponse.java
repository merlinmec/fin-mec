package com.mecfin.dashboard.api;

import com.mecfin.dashboard.application.CategoryExpense;
import java.math.BigDecimal;
import java.util.UUID;

public record CategoryExpenseResponse(UUID categoryId, String categoryName, BigDecimal amount) {

    public static CategoryExpenseResponse from(CategoryExpense expense) {
        return new CategoryExpenseResponse(expense.categoryId(), expense.categoryName(), expense.amount());
    }
}
