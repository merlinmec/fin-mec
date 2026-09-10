import { QueryClient } from "@tanstack/react-query";
import { ApiError } from "@/api/client";

/**
 * Defaults do TanStack Query para o fin-mec:
 *  - o servidor e a fonte de verdade; o cache so espelha. staleTime curto.
 *  - nao re-tentar erros 4xx (payload/estado invalido nao melhora com retry);
 *    401 ja e tratado pelo handler global do cliente HTTP.
 */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      retry: (failureCount, error) => {
        if (error instanceof ApiError && error.status >= 400 && error.status < 500) {
          return false;
        }
        return failureCount < 2;
      },
      refetchOnWindowFocus: false,
    },
    mutations: {
      retry: false,
    },
  },
});
