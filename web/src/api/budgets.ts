import { api } from "./client";

/** Espelha com.mecfin.budget.api.BudgetResponse — spent/percentageUsed sao calculados pelo backend na leitura, nunca recalcular no frontend. */
export interface Budget {
  id: string;
  categoryId: string;
  /** YearMonth, formato yyyy-MM. Imutavel apos criado (junto com categoryId). */
  referenceMonth: string;
  amount: number;
  spent: number;
  /** 0-100+ (pode passar de 100 quando o gasto estoura o limite). */
  percentageUsed: number;
  createdAt: string;
  updatedAt: string;
}

/** Espelha com.mecfin.budget.api.CreateBudgetRequest. */
export interface CreateBudgetPayload {
  categoryId: string;
  referenceMonth: string;
  amount: number;
}

/** Espelha com.mecfin.budget.api.UpdateBudgetRequest — so o valor planejado e editavel. */
export interface UpdateBudgetPayload {
  amount: number;
}

export function listBudgets(referenceMonth?: string): Promise<Budget[]> {
  const query = referenceMonth ? `?referenceMonth=${referenceMonth}` : "";
  return api.get<Budget[]>(`/budgets${query}`);
}

export function createBudget(payload: CreateBudgetPayload): Promise<Budget> {
  return api.post<Budget>("/budgets", payload);
}

export function updateBudget(id: string, payload: UpdateBudgetPayload): Promise<Budget> {
  return api.put<Budget>(`/budgets/${id}`, payload);
}

/** DELETE /budgets/{id} — hard delete (diferente de Account/Category/Transaction, Budget nao tem soft delete). */
export function deleteBudget(id: string): Promise<void> {
  return api.del<void>(`/budgets/${id}`);
}
