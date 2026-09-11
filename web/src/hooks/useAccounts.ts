import { useQuery } from "@tanstack/react-query";
import { listAccounts } from "@/api/accounts";

/**
 * Em hooks/ (nao routes/accounts/) pelo mesmo motivo de useCategories: a
 * lista de contas e consumida por mais gente que a AccountsPage — AccountSelect
 * (components/) e, a partir da FE-4, os formularios de lancamento/transferencia/
 * parcelamento.
 */
export const accountsQueryKey = ["accounts"] as const;

export function useAccounts() {
  return useQuery({ queryKey: accountsQueryKey, queryFn: listAccounts });
}
