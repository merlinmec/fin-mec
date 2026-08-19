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
 * {@code projectedBalance} é uma previsão simples: saldo disponível menos as contas a pagar
 * (Bill) OPEN com vencimento até o fim do mês de referência. Lançamentos recorrentes
 * (Transaction/Bill com recurrenceRule) NÃO entram nessa previsão - recorrência ainda é só
 * metadado (decisão do usuário nas Fases 5/6), sem motor que gere as ocorrências futuras, então
 * não há como somar o que ainda não existe como registro.
 */
public record DashboardSummary(
        YearMonth referenceMonth,
        List<AccountBalance> accountBalances,
        BigDecimal totalLedgerBalance,
        BigDecimal totalAvailableBalance,
        BigDecimal monthlyIncome,
        BigDecimal monthlyExpense,
        BigDecimal projectedBalance,
        List<BillView> upcomingBills,
        List<CategoryExpense> expensesByCategory,
        List<BudgetView> budgets) {
}
