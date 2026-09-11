import { useQuery } from "@tanstack/react-query";
import { listCategories } from "@/api/categories";

/**
 * Em hooks/ (nao routes/categories/) porque quem consome a lista de
 * categorias nao e so a CategoriesPage — CategorySelect (components/) e,
 * nas fases seguintes, lancamento/orcamento/conta a pagar/cartao tambem
 * precisam dela. routes/categories/hooks.ts cuida so das mutacoes de CRUD
 * (proprias daquela tela) e reusa essa key pra invalidar o cache.
 */
export const categoriesQueryKey = ["categories"] as const;

export function useCategories() {
  return useQuery({ queryKey: categoriesQueryKey, queryFn: listCategories });
}
