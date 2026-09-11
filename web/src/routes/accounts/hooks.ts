import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import {
  createAccount,
  deleteAccount,
  listAccounts,
  updateAccount,
  type CreateAccountPayload,
  type UpdateAccountPayload,
} from "@/api/accounts";
import { getErrorMessage } from "@/lib/errors";

const accountsKey = ["accounts"] as const;

export function useAccounts() {
  return useQuery({ queryKey: accountsKey, queryFn: listAccounts });
}

export function useCreateAccount() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateAccountPayload) => createAccount(payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: accountsKey });
      toast.success("Conta criada.");
    },
    onError: (err) => {
      toast.error(getErrorMessage(err, "Não foi possível criar a conta."));
    },
  });
}

export function useUpdateAccount() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: UpdateAccountPayload }) => updateAccount(id, payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: accountsKey });
      toast.success("Conta atualizada.");
    },
    onError: (err) => {
      toast.error(getErrorMessage(err, "Não foi possível atualizar a conta."));
    },
  });
}

export function useDeleteAccount() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => deleteAccount(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: accountsKey });
      toast.success("Conta excluída.");
    },
    onError: (err) => {
      toast.error(getErrorMessage(err, "Não foi possível excluir a conta."));
    },
  });
}
