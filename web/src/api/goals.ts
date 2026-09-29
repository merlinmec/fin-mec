import { api } from "./client";

export type GoalStatus = "ACTIVE" | "COMPLETED" | "OVERDUE" | "ARCHIVED";

export const GOAL_STATUS_LABELS: Record<GoalStatus, string> = {
  ACTIVE: "Em andamento",
  COMPLETED: "Concluída",
  OVERDUE: "Prazo vencido",
  ARCHIVED: "Arquivada",
};

/** Espelha com.mecfin.goal.api.GoalResponse — guardado, percentual e "quanto por mês" vêm prontos do backend. */
export interface Goal {
  id: string;
  name: string;
  targetAmount: number;
  targetDate: string | null;
  color: string | null;
  icon: string | null;
  archived: boolean;
  savedAmount: number;
  savedThisMonth: number;
  remainingAmount: number;
  progressPercent: number;
  status: GoalStatus;
  monthsRemaining: number | null;
  monthlyNeeded: number | null;
  createdAt: string;
}

export interface GoalContribution {
  id: string;
  amount: number;
  date: string;
  note: string | null;
  createdAt: string;
}

/** Espelha com.mecfin.goal.api.GoalRequest. */
export interface GoalPayload {
  name: string;
  targetAmount: number;
  targetDate?: string | null;
  color?: string | null;
  icon?: string | null;
  archived: boolean;
}

export function listGoals(includeArchived = false): Promise<Goal[]> {
  return api.get<Goal[]>(`/goals${includeArchived ? "?includeArchived=true" : ""}`);
}

export function createGoal(payload: GoalPayload): Promise<Goal> {
  return api.post<Goal>("/goals", payload);
}

export function updateGoal(id: string, payload: GoalPayload): Promise<Goal> {
  return api.put<Goal>(`/goals/${id}`, payload);
}

export function deleteGoal(id: string): Promise<void> {
  return api.del<void>(`/goals/${id}`);
}

export function listContributions(goalId: string): Promise<GoalContribution[]> {
  return api.get<GoalContribution[]>(`/goals/${goalId}/contributions`);
}

/** amount > 0 = aporte; < 0 = resgate. */
export function contribute(
  goalId: string,
  amount: number,
  note?: string,
  date?: string,
): Promise<Goal> {
  return api.post<Goal>(`/goals/${goalId}/contributions`, {
    amount,
    note: note || undefined,
    date: date || undefined,
  });
}

export function removeContribution(goalId: string, contributionId: string): Promise<Goal> {
  return api.del<Goal>(`/goals/${goalId}/contributions/${contributionId}`);
}
