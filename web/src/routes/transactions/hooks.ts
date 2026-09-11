import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import {
  cancelTransaction,
  createInstallments,
  createTransaction,
  createTransfer,
  searchTransactions,
  updateTransaction,
  type CreateInstallmentPayload,
  type CreateTransactionPayload,
  type CreateTransferPayload,
  type TransactionSearchParams,
  type UpdateTransactionPayload,
} from "@/api/transactions";
import { accountsQueryKey } from "@/hooks/useAccounts";
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

// Todas as mutacoes invalidam tanto "transactions" (qualquer filtro) quanto
// "accounts" — lancar/transferir/cancelar nao muda initialBalance, mas o
// saldo hoje e so essa foto estatica (Fase de Dashboard ainda nao chegou no
// frontend); invalidar aqui mantem a AccountsPage coerente se o usuario
// navegar pra la logo em seguida, sem custo real (query so refaz se estiver montada).
function invalidateAll(queryClient: ReturnType<typeof useQueryClient>) {
  void queryClient.invalidateQueries({ queryKey: ["transactions"] });
  void queryClient.invalidateQueries({ queryKey: accountsQueryKey });
}

export function useCreateTransaction() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateTransactionPayload) => createTransaction(payload),
    onSuccess: () => {
      invalidateAll(queryClient);
      toast.success("Lançamento criado.");
    },
    onError: (err) => toast.error(getErrorMessage(err, "Não foi possível criar o lançamento.")),
  });
}

export function useUpdateTransaction() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: UpdateTransactionPayload }) => updateTransaction(id, payload),
    onSuccess: () => {
      invalidateAll(queryClient);
      toast.success("Lançamento atualizado.");
    },
    onError: (err) => toast.error(getErrorMessage(err, "Não foi possível atualizar o lançamento.")),
  });
}

export function useCreateTransfer() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateTransferPayload) => createTransfer(payload),
    onSuccess: () => {
      invalidateAll(queryClient);
      toast.success("Transferência registrada.");
    },
    onError: (err) => toast.error(getErrorMessage(err, "Não foi possível registrar a transferência.")),
  });
}

export function useCreateInstallments() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateInstallmentPayload) => createInstallments(payload),
    onSuccess: (created) => {
      invalidateAll(queryClient);
      toast.success(`${created.length} parcelas criadas.`);
    },
    onError: (err) => toast.error(getErrorMessage(err, "Não foi possível criar o parcelamento.")),
  });
}

export function useCancelTransaction() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => cancelTransaction(id),
    onSuccess: () => {
      invalidateAll(queryClient);
      toast.success("Lançamento cancelado.");
    },
    onError: (err) => toast.error(getErrorMessage(err, "Não foi possível cancelar o lançamento.")),
  });
}
