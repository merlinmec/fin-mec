import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { createCategory, deleteCategory, updateCategory, type CategoryPayload } from "@/api/categories";
import { categoriesQueryKey } from "@/hooks/useCategories";
import { getErrorMessage } from "@/lib/errors";

export { useCategories } from "@/hooks/useCategories";

export function useCreateCategory() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: CategoryPayload) => createCategory(payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: categoriesQueryKey });
      toast.success("Categoria criada.");
    },
    onError: (err) => {
      toast.error(getErrorMessage(err, "Não foi possível criar a categoria."));
    },
  });
}

export function useUpdateCategory() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: CategoryPayload }) => updateCategory(id, payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: categoriesQueryKey });
      toast.success("Categoria atualizada.");
    },
    onError: (err) => {
      toast.error(getErrorMessage(err, "Não foi possível atualizar a categoria."));
    },
  });
}

export function useDeleteCategory() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => deleteCategory(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: categoriesQueryKey });
      toast.success("Categoria excluída.");
    },
    onError: (err) => {
      toast.error(getErrorMessage(err, "Não foi possível excluir a categoria."));
    },
  });
}
