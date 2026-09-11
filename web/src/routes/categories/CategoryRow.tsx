import { useState } from "react";
import type { Category } from "@/api/categories";
import { CATEGORY_TYPE_LABELS } from "@/api/categories";
import { CategoryIcon } from "@/components/CategoryIcon";
import { Button } from "@/components/ui/button";
import { useDeleteCategory } from "./hooks";

interface CategoryRowProps {
  category: Category;
  onEdit: (category: Category) => void;
}

/** Mesmo padrao de confirmacao em duas etapas do AccountRow (FE-2). */
export function CategoryRow({ category, onEdit }: CategoryRowProps) {
  const [confirming, setConfirming] = useState(false);
  const deleteCategory = useDeleteCategory();

  return (
    <tr className="border-b border-border last:border-0">
      <td className="py-3 pr-4 pl-4">
        <div className="flex items-center gap-2 font-medium">
          <CategoryIcon icon={category.icon} color={category.color} />
          {category.name}
          {category.systemDefault && (
            <span className="rounded-full bg-muted px-2 py-0.5 text-xs font-normal text-muted-foreground">
              Padrão
            </span>
          )}
        </div>
      </td>
      <td className="py-3 pr-4 text-muted-foreground">{CATEGORY_TYPE_LABELS[category.type]}</td>
      <td className="py-3 pr-4 text-right">
        {category.systemDefault ? (
          <span className="text-xs text-muted-foreground">Não editável</span>
        ) : (
          <div className="flex justify-end gap-2">
            <Button variant="outline" size="sm" onClick={() => onEdit(category)} disabled={confirming}>
              Editar
            </Button>
            {confirming ? (
              <>
                <Button
                  variant="destructive"
                  size="sm"
                  disabled={deleteCategory.isPending}
                  onClick={() => deleteCategory.mutate(category.id, { onSettled: () => setConfirming(false) })}
                >
                  Confirmar
                </Button>
                <Button variant="ghost" size="sm" onClick={() => setConfirming(false)}>
                  Cancelar
                </Button>
              </>
            ) : (
              <Button variant="outline" size="sm" onClick={() => setConfirming(true)}>
                Excluir
              </Button>
            )}
          </div>
        )}
      </td>
    </tr>
  );
}
