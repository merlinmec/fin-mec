import { useQuery } from "@tanstack/react-query";
import { getDemoStatus } from "@/api/demo";

export const demoStatusQueryKey = ["demo-status"] as const;

/** O deploy é a demonstração pública? Não muda com o app aberto, então busca uma vez só. */
export function useDemoStatus() {
  return useQuery({
    queryKey: demoStatusQueryKey,
    queryFn: getDemoStatus,
    staleTime: Infinity,
    // Não é dado de usuário: sobrevive à limpeza do cache na troca de sessão (AuthProvider).
    meta: { global: true },
  });
}
