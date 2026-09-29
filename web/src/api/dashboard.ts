import { api } from "./client";
import type { AccountType } from "./accounts";
import type { Bill } from "./bills";
import type { Budget } from "./budgets";

/** Espelha com.mecfin.dashboard.api.AccountBalanceResponse. */
export interface AccountBalance {
  accountId: string;
  accountName: string;
  accountType: AccountType;
  /** Saldo contabil: todos os lancamentos POSTED, passados e futuros. */
  ledgerBalance: number;
  /** Saldo disponivel: so os POSTED ate hoje. */
  availableBalance: number;
}

/** Espelha com.mecfin.dashboard.api.CategoryExpenseResponse. */
export interface CategoryExpense {
  categoryId: string;
  categoryName: string;
  amount: number;
}

/** Espelha com.mecfin.dashboard.api.DashboardResponse. Tudo aqui ja vem calculado do backend — nunca recalcular no frontend. */
export interface Dashboard {
  referenceMonth: string;
  accountBalances: AccountBalance[];
  totalLedgerBalance: number;
  totalAvailableBalance: number;
  monthlyIncome: number;
  monthlyExpense: number;
  /** Ainda a receber/pagar na competencia do mes (lancamentos previstos, ex.: ocorrencias de fixos). */
  pendingIncome: number;
  pendingExpense: number;
  /** Saldo disponivel - contas a pagar em aberto ate o fim do mes + lancamentos previstos ate essa data. */
  projectedBalance: number;
  upcomingBills: Bill[];
  expensesByCategory: CategoryExpense[];
  budgets: Budget[];
}

export function getDashboard(month?: string): Promise<Dashboard> {
  const query = month ? `?month=${month}` : "";
  return api.get<Dashboard>(`/dashboard${query}`);
}
