package com.mecfin.report.application;

import java.math.BigDecimal;
import java.time.YearMonth;

// Um mês do fluxo de caixa. income/expense/net = efetivado na competência; pending* = previsto
// (ainda não efetivado); closingBalance = saldo contábil no último dia do mês.
public record CashFlowMonth(
        YearMonth month,
        BigDecimal income,
        BigDecimal expense,
        BigDecimal net,
        BigDecimal pendingIncome,
        BigDecimal pendingExpense,
        BigDecimal closingBalance) {
}
