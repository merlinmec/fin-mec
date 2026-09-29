import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { getCashFlow, getCategoryReport, type ReportRange } from "@/api/reports";
import {
  contribute,
  createGoal,
  deleteGoal,
  listContributions,
  listGoals,
  removeContribution,
  updateGoal,
  type GoalPayload,
} from "@/api/goals";
import { createTag, deleteTag, listTags, updateTag, type TagPayload } from "@/api/tags";
import { listRecurringSeries, stopRecurringSeries, type EntryType } from "@/api/transactions";
import { getErrorMessage } from "@/lib/errors";

/**
 * Hooks de dados das features da Fase 11-13 (relatórios, metas, tags, fixos).
 * Toda mutação que mexe em lançamento invalida também dashboard e relatórios:
 * esses números são sempre recalculados no backend, nunca aqui.
 */

export function invalidateFinancialViews(queryClient: ReturnType<typeof useQueryClient>) {
  for (const key of [
    "transactions",
    "dashboard",
    "reports",
    "recurring-series",
    "accounts",
    "budgets",
    "bills",
    "tags",
    "insights",
  ]) {
    void queryClient.invalidateQueries({ queryKey: [key] });
  }
}

// ---- relatórios ----

export function useCashFlow(range: ReportRange) {
  return useQuery({
    queryKey: ["reports", "cash-flow", range],
    queryFn: () => getCashFlow(range),
    placeholderData: keepPreviousData,
  });
}

export function useCategoryReport(range: ReportRange, type: EntryType) {
  return useQuery({
    queryKey: ["reports", "categories", range, type],
    queryFn: () => getCategoryReport(range, type),
    placeholderData: keepPreviousData,
  });
}

// ---- metas ----

export function useGoals(includeArchived = false) {
  return useQuery({
    queryKey: ["goals", includeArchived],
    queryFn: () => listGoals(includeArchived),
  });
}

export function useGoalContributions(goalId: string | undefined) {
  return useQuery({
    queryKey: ["goals", "contributions", goalId],
    queryFn: () => listContributions(goalId!),
    enabled: goalId !== undefined,
  });
}

function useGoalMutation<TArgs>(
  fn: (args: TArgs) => Promise<unknown>,
  success: string,
  failure: string,
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["goals"] });
      toast.success(success);
    },
    onError: (err) => toast.error(getErrorMessage(err, failure)),
  });
}

export function useSaveGoal() {
  return useGoalMutation(
    ({ id, payload }: { id?: string; payload: GoalPayload }) =>
      id ? updateGoal(id, payload) : createGoal(payload),
    "Meta salva.",
    "Não foi possível salvar a meta.",
  );
}

export function useDeleteGoal() {
  return useGoalMutation(
    (id: string) => deleteGoal(id),
    "Meta excluída.",
    "Não foi possível excluir a meta.",
  );
}

export function useContribute() {
  return useGoalMutation(
    ({ goalId, amount, note }: { goalId: string; amount: number; note?: string }) =>
      contribute(goalId, amount, note),
    "Movimentação registrada na meta.",
    "Não foi possível registrar a movimentação.",
  );
}

export function useRemoveContribution() {
  return useGoalMutation(
    ({ goalId, contributionId }: { goalId: string; contributionId: string }) =>
      removeContribution(goalId, contributionId),
    "Movimentação removida.",
    "Não foi possível remover a movimentação.",
  );
}

// ---- tags ----

export function useTags() {
  return useQuery({ queryKey: ["tags"], queryFn: listTags, staleTime: 60_000 });
}

export function useSaveTag() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, payload }: { id?: string; payload: TagPayload }) =>
      id ? updateTag(id, payload) : createTag(payload),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ["tags"] }),
    onError: (err) => toast.error(getErrorMessage(err, "Não foi possível salvar a tag.")),
  });
}

export function useDeleteTag() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => deleteTag(id),
    onSuccess: () => {
      invalidateFinancialViews(queryClient);
      toast.success("Tag excluída — os lançamentos continuam, só sem ela.");
    },
    onError: (err) => toast.error(getErrorMessage(err, "Não foi possível excluir a tag.")),
  });
}

// ---- lançamentos fixos ----

export function useRecurringSeries() {
  return useQuery({ queryKey: ["recurring-series"], queryFn: listRecurringSeries });
}

export function useStopRecurringSeries() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => stopRecurringSeries(id),
    onSuccess: () => {
      invalidateFinancialViews(queryClient);
      toast.success("O lançamento fixo não vai mais se repetir.");
    },
    onError: (err) =>
      toast.error(getErrorMessage(err, "Não foi possível encerrar o lançamento fixo.")),
  });
}
