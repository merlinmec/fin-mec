import { useState } from "react";
import type { Category } from "@/api/categories";
import { Button } from "@/components/ui/button";
import { useCategories } from "./hooks";
import { CategoryRow } from "./CategoryRow";
import { CategoryFormDialog } from "./CategoryFormDialog";

export function CategoriesPage() {
  const { data: categories, isPending, isError } = useCategories();
  const [formOpen, setFormOpen] = useState(false);
  const [editingCategory, setEditingCategory] = useState<Category | undefined>(undefined);

  function openCreate() {
    setEditingCategory(undefined);
    setFormOpen(true);
  }

  function openEdit(category: Category) {
    setEditingCategory(category);
    setFormOpen(true);
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold tracking-tight">Categorias</h1>
          <p className="text-sm text-muted-foreground">
            As categorias padrão do sistema valem pra todo household e não podem ser editadas ou excluídas.
          </p>
        </div>
        <Button onClick={openCreate}>Nova categoria</Button>
      </div>

      {isPending && <p className="text-sm text-muted-foreground">Carregando categorias…</p>}

      {isError && (
        <p className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
          Não foi possível carregar as categorias. Tente recarregar a página.
        </p>
      )}

      {categories && categories.length > 0 && (
        <div className="overflow-x-auto rounded-xl border border-border">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-border text-left text-muted-foreground">
                <th className="py-2 pr-4 pl-4 font-medium">Nome</th>
                <th className="py-2 pr-4 font-medium">Tipo</th>
                <th className="py-2 pr-4 font-medium" />
              </tr>
            </thead>
            <tbody>
              {categories.map((category) => (
                <CategoryRow key={category.id} category={category} onEdit={openEdit} />
              ))}
            </tbody>
          </table>
        </div>
      )}

      <CategoryFormDialog open={formOpen} onOpenChange={setFormOpen} category={editingCategory} />
    </div>
  );
}
