import { api } from "./client";

/** Espelha com.mecfin.category.domain.CategoryType. */
export type CategoryType = "INCOME" | "EXPENSE";

export const CATEGORY_TYPES = ["EXPENSE", "INCOME"] as const satisfies readonly CategoryType[];

export const CATEGORY_TYPE_LABELS: Record<CategoryType, string> = {
  EXPENSE: "Despesa",
  INCOME: "Receita",
};

/** Espelha com.mecfin.category.api.CategoryResponse. */
export interface Category {
  id: string;
  name: string;
  type: CategoryType;
  parentId: string | null;
  color: string | null;
  icon: string | null;
  systemDefault: boolean;
  createdAt: string;
  updatedAt: string;
}

/**
 * Espelha com.mecfin.category.api.CreateCategoryRequest/UpdateCategoryRequest
 * (os dois tem o mesmo shape). parentId de fora do escopo da FE-3 — o form
 * nao oferece subcategoria por enquanto, so o backend ja suporta.
 */
export interface CategoryPayload {
  name: string;
  type: CategoryType;
  color?: string;
  icon?: string;
}

export function listCategories(): Promise<Category[]> {
  return api.get<Category[]>("/categories");
}

export function createCategory(payload: CategoryPayload): Promise<Category> {
  return api.post<Category>("/categories", payload);
}

export function updateCategory(id: string, payload: CategoryPayload): Promise<Category> {
  return api.put<Category>(`/categories/${id}`, payload);
}

/** DELETE /categories/{id} — soft delete; categorias padrao do sistema nao podem ser excluidas (nem editadas). */
export function deleteCategory(id: string): Promise<void> {
  return api.del<void>(`/categories/${id}`);
}
