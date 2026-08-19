package com.mecfin.dashboard.api;

import com.mecfin.bill.api.BillResponse;
import com.mecfin.budget.api.BudgetResponse;
import com.mecfin.dashboard.application.DashboardSummary;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

public record DashboardResponse(
        YearMonth referenceMonth,
        List<AccountBalanceResponse> accountBalances,
        BigDecimal totalLedgerBalance,
        BigDecimal totalAvailableBalance,
        BigDecimal monthlyIncome,
        BigDecimal monthlyExpense,
        BigDecimal projectedBalance,
        List<BillResponse> upcomingBills,
        List<CategoryExpenseResponse> expensesByCategory,
        List<BudgetResponse> budgets) {

    public static DashboardResponse from(DashboardSummary summary) {
        return new DashboardResponse(
                summary.referenceMonth(),
                summary.accountBalances().stream().map(AccountBalanceResponse::from).toList(),
                summary.totalLedgerBalance(),
                summary.totalAvailableBalance(),
                summary.monthlyIncome(),
                summary.monthlyExpense(),
                summary.projectedBalance(),
                summary.upcomingBills().stream().map(BillResponse::from).toList(),
                summary.expensesByCategory().stream().map(CategoryExpenseResponse::from).toList(),
                summary.budgets().stream().map(BudgetResponse::from).toList());
    }
}
