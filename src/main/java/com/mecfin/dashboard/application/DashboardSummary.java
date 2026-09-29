package com.mecfin.dashboard.application;

import com.mecfin.bill.application.BillView;
import com.mecfin.budget.application.BudgetView;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

/**
 * Modelo de leitura agregado do dashboard - dashboard não tem entidade nem tabela própria,
 * é puramente composição de account/transaction/bill/budget (ver DashboardService).
 *
 * {@code projectedBalance}: saldo disponível, menos as contas a pagar (Bill) OPEN com
 * vencimento até o fim do mês de referência, mais o saldo dos lançamentos previstos (PENDING)
 * até essa data. Desde a Fase 11 o motor de recorrência materializa as ocorrências futuras dos
 * fixos como PENDING, então elas entram aqui sem nenhuma regra especial.
 *
 * {@code pendingIncome}/{@code pendingExpense}: o que ainda falta receber/pagar na competência
 * do mês (lançamentos PENDING), separado do realizado em monthlyIncome/monthlyExpense.
 */
public record DashboardSummary(
        YearMonth referenceMonth,
        List<AccountBalance> accountBalances,
        BigDecimal totalLedgerBalance,
        BigDecimal totalAvailableBalance,
        BigDecimal monthlyIncome,
        BigDecimal monthlyExpense,
        BigDecimal pendingIncome,
        BigDecimal pendingExpense,
        BigDecimal projectedBalance,
        List<BillView> upcomingBills,
        List<CategoryExpense> expensesByCategory,
        List<BudgetView> budgets) {
}
