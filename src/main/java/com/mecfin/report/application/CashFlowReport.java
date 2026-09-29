package com.mecfin.report.application;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

// savingsRate = net / totalIncome em %, null quando não houve receita no período.
public record CashFlowReport(
        YearMonth from,
        YearMonth to,
        BigDecimal openingBalance,
        BigDecimal closingBalance,
        BigDecimal totalIncome,
        BigDecimal totalExpense,
        BigDecimal net,
        BigDecimal averageMonthlyExpense,
        BigDecimal savingsRate,
        List<CashFlowMonth> months) {
}
