import { api } from "./client";
import type { EntryType } from "./transactions";

/** Espelha com.mecfin.report.application.CashFlowMonth. */
export interface CashFlowMonth {
  month: string;
  income: number;
  expense: number;
  net: number;
  pendingIncome: number;
  pendingExpense: number;
  /** Saldo contábil no último dia do mês. */
  closingBalance: number;
}

/** Espelha com.mecfin.report.application.CashFlowReport. Tudo já calculado no backend. */
export interface CashFlowReport {
  from: string;
  to: string;
  openingBalance: number;
  closingBalance: number;
  totalIncome: number;
  totalExpense: number;
  net: number;
  averageMonthlyExpense: number;
  /** % da receita que sobrou; null quando não houve receita. */
  savingsRate: number | null;
  months: CashFlowMonth[];
}

export interface MonthAmount {
  month: string;
  amount: number;
}

/** Espelha com.mecfin.report.application.CategoryReportLine. categoryId null = sem categoria. */
export interface CategoryReportLine {
  categoryId: string | null;
  name: string;
  color: string | null;
  icon: string | null;
  total: number;
  share: number;
  previousTotal: number;
  /** Variação contra o período anterior; null quando o anterior foi zero. */
  changePercent: number | null;
  monthlyAverage: number;
  monthly: MonthAmount[];
}

export interface CategoryReport {
  from: string;
  to: string;
  type: EntryType;
  total: number;
  previousTotal: number;
  changePercent: number | null;
  categories: CategoryReportLine[];
}

export interface ReportRange {
  from: string;
  to: string;
}

export function getCashFlow({ from, to }: ReportRange): Promise<CashFlowReport> {
  return api.get<CashFlowReport>(`/reports/cash-flow?from=${from}&to=${to}`);
}

export function getCategoryReport(
  { from, to }: ReportRange,
  type: EntryType,
): Promise<CategoryReport> {
  return api.get<CategoryReport>(`/reports/categories?from=${from}&to=${to}&type=${type}`);
}
