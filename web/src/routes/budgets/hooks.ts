import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import {
  createBudget,
  deleteBudget,
  listBudgets,
  updateBudget,
  type CreateBudgetPayload,
  type UpdateBudgetPayload,
} from "@/api/budgets";
import { getErrorMessage } from "@/lib/errors";

export function useBudgets(referenceMonth: string) {
  return useQuery({
    queryKey: ["budgets", referenceMonth],
    queryFn: () => listBudgets(referenceMonth),
  });
}

// Invalida o prefixo "budgets" inteiro (qualquer mes), nao so o mes atual —
// mais simples que rastrear qual mes cada mutacao afeta, e o custo e so
// refazer queries que estejam de fato montadas.
function invalidateBudgets(queryClient: ReturnType<typeof useQueryClient>) {
  void queryClient.invalidateQueries({ queryKey: ["budgets"] });
}

export function useCreateBudget() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateBudgetPayload) => createBudget(payload),
    onSuccess: () => {
      invalidateBudgets(queryClient);
      toast.success("Orçamento criado.");
    },
    onError: (err) => toast.error(getErrorMessage(err, "Não foi possível criar o orçamento.")),
  });
}

export function useUpdateBudget() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: UpdateBudgetPayload }) => updateBudget(id, payload),
    onSuccess: () => {
      invalidateBudgets(queryClient);
      toast.success("Orçamento atualizado.");
    },
    onError: (err) => toast.error(getErrorMessage(err, "Não foi possível atualizar o orçamento.")),
  });
}

export function useDeleteBudget() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => deleteBudget(id),
    onSuccess: () => {
      invalidateBudgets(queryClient);
      toast.success("Orçamento excluído.");
    },
    onError: (err) => toast.error(getErrorMessage(err, "Não foi possível excluir o orçamento.")),
  });
}
