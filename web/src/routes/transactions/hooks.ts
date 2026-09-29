import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import {
  cancelTransaction,
  confirmTransaction,
  createInstallments,
  createTransaction,
  createTransfer,
  searchTransactions,
  updateTransaction,
  type CreateInstallmentPayload,
  type CreateTransactionPayload,
  type CreateTransferPayload,
  type EditScope,
  type TransactionSearchParams,
  type UpdateTransactionPayload,
} from "@/api/transactions";
import { invalidateFinancialViews } from "@/hooks/useFeatureData";
import { getErrorMessage } from "@/lib/errors";

const transactionsKey = (params: TransactionSearchParams) => ["transactions", params] as const;

export function useTransactions(params: TransactionSearchParams) {
  return useQuery({
    queryKey: transactionsKey(params),
    queryFn: () => searchTransactions(params),
    // Evita o flash de loading ao trocar so a pagina/filtro — mantem a pagina
    // anterior visivel (meio opaca, ver TransactionsPage) ate a nova chegar.
    placeholderData: keepPreviousData,
  });
}

// Toda mutacao de lancamento mexe em saldo, dashboard, orcamento e relatorios — todos
// recalculados no backend, entao basta invalidar (ver invalidateFinancialViews).

export function useCreateTransaction() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateTransactionPayload) => createTransaction(payload),
    onSuccess: (created) => {
      invalidateFinancialViews(queryClient);
      toast.success(
        created.recurrenceSeriesId
          ? "Lançamento fixo criado — os próximos já estão na agenda."
          : "Lançamento criado.",
      );
    },
    onError: (err) => toast.error(getErrorMessage(err, "Não foi possível criar o lançamento.")),
  });
}

export function useUpdateTransaction() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      id,
      payload,
      scope,
    }: {
      id: string;
      payload: UpdateTransactionPayload;
      scope?: EditScope;
    }) => updateTransaction(id, payload, scope),
    onSuccess: (_, { scope }) => {
      invalidateFinancialViews(queryClient);
      toast.success(
        scope === "THIS_AND_FUTURE"
          ? "Lançamento e próximas ocorrências atualizados."
          : "Lançamento atualizado.",
      );
    },
    onError: (err) => toast.error(getErrorMessage(err, "Não foi possível atualizar o lançamento.")),
  });
}

export function useCreateTransfer() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateTransferPayload) => createTransfer(payload),
    onSuccess: () => {
      invalidateFinancialViews(queryClient);
      toast.success("Transferência registrada.");
    },
    onError: (err) =>
      toast.error(getErrorMessage(err, "Não foi possível registrar a transferência.")),
  });
}

export function useCreateInstallments() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateInstallmentPayload) => createInstallments(payload),
    onSuccess: (created) => {
      invalidateFinancialViews(queryClient);
      toast.success(`${created.length} parcelas criadas.`);
    },
    onError: (err) => toast.error(getErrorMessage(err, "Não foi possível criar o parcelamento.")),
  });
}

export function useCancelTransaction() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, scope }: { id: string; scope?: EditScope }) => cancelTransaction(id, scope),
    onSuccess: (_, { scope }) => {
      invalidateFinancialViews(queryClient);
      toast.success(
        scope === "THIS_AND_FUTURE"
          ? "Lançamento e próximas ocorrências cancelados."
          : "Lançamento cancelado.",
      );
    },
    onError: (err) => toast.error(getErrorMessage(err, "Não foi possível cancelar o lançamento.")),
  });
}

export function useConfirmTransaction() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => confirmTransaction(id),
    onSuccess: (confirmed) => {
      invalidateFinancialViews(queryClient);
      toast.success(`"${confirmed.description}" efetivado.`);
    },
    onError: (err) => toast.error(getErrorMessage(err, "Não foi possível efetivar o lançamento.")),
  });
}
