import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import {
  cancelBill,
  createBill,
  listBills,
  payBill,
  updateBill,
  type BillPayload,
  type BillStatus,
  type PayBillPayload,
} from "@/api/bills";
import { accountsQueryKey } from "@/hooks/useAccounts";
import { getErrorMessage } from "@/lib/errors";

export function useBills(status?: BillStatus) {
  return useQuery({
    queryKey: ["bills", status ?? "ALL"],
    queryFn: () => listBills(status),
  });
}

// Pagar uma conta cria uma Transaction real (EXPENSE) — invalida lancamentos e
// contas (saldo) tambem, nao so "bills". Mesmo espirito do invalidateAll de
// transactions/hooks.ts (FE-4).
function invalidateBills(queryClient: ReturnType<typeof useQueryClient>) {
  void queryClient.invalidateQueries({ queryKey: ["bills"] });
  void queryClient.invalidateQueries({ queryKey: ["transactions"] });
  void queryClient.invalidateQueries({ queryKey: accountsQueryKey });
}

export function useCreateBill() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: BillPayload) => createBill(payload),
    onSuccess: () => {
      invalidateBills(queryClient);
      toast.success("Conta a pagar criada.");
    },
    onError: (err) => toast.error(getErrorMessage(err, "Não foi possível criar a conta a pagar.")),
  });
}

export function useUpdateBill() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: BillPayload }) => updateBill(id, payload),
    onSuccess: () => {
      invalidateBills(queryClient);
      toast.success("Conta a pagar atualizada.");
    },
    onError: (err) => toast.error(getErrorMessage(err, "Não foi possível atualizar a conta a pagar.")),
  });
}

export function usePayBill() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: PayBillPayload }) => payBill(id, payload),
    onSuccess: () => {
      invalidateBills(queryClient);
    },
    onError: (err) => toast.error(getErrorMessage(err, "Não foi possível registrar o pagamento.")),
  });
}

export function useCancelBill() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => cancelBill(id),
    onSuccess: () => {
      invalidateBills(queryClient);
      toast.success("Conta a pagar cancelada.");
    },
    onError: (err) => toast.error(getErrorMessage(err, "Não foi possível cancelar a conta a pagar.")),
  });
}
