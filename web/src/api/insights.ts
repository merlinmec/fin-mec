import { api } from "./client";

export type ForecastEventKind = "TRANSACTION" | "BILL" | "CREDIT_CARD_INVOICE";

/** amount com sinal: positivo entra, negativo sai. */
export interface ForecastEvent {
  date: string;
  kind: ForecastEventKind;
  description: string;
  amount: number;
  overdue: boolean;
}

export interface ForecastPoint {
  date: string;
  balance: number;
  income: number;
  expense: number;
  events: ForecastEvent[];
}

/** Espelha com.mecfin.insight.application.BalanceForecast. */
export interface BalanceForecast {
  from: string;
  to: string;
  startBalance: number;
  endBalance: number;
  lowestBalance: number;
  lowestDate: string;
  firstNegativeDate: string | null;
  totalIncome: number;
  totalExpense: number;
  points: ForecastPoint[];
}

export interface CategoryHighlight {
  categoryId: string | null;
  name: string;
  color: string | null;
  icon: string | null;
  total: number;
  share: number | null;
  previousTotal: number | null;
  changePercent: number | null;
}

/** Espelha com.mecfin.insight.application.MonthlySummary. */
export interface MonthlySummary {
  month: string;
  hasData: boolean;
  income: number;
  expense: number;
  net: number;
  savingsRate: number | null;
  previousExpense: number;
  expenseChangePercent: number | null;
  topCategories: CategoryHighlight[];
  biggestIncrease: CategoryHighlight | null;
  headlines: string[];
}

export function getForecast(days = 90): Promise<BalanceForecast> {
  return api.get<BalanceForecast>(`/insights/forecast?days=${days}`);
}

export function getMonthlySummary(month?: string): Promise<MonthlySummary> {
  return api.get<MonthlySummary>(
    month ? `/insights/monthly-summary?month=${month}` : "/insights/monthly-summary",
  );
}
