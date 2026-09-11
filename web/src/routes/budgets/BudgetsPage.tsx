import { useMemo, useState } from "react";
import type { Budget } from "@/api/budgets";
import { useCategories } from "@/hooks/useCategories";
import { MonthSelector } from "@/components/MonthSelector";
import { Button } from "@/components/ui/button";
import { currentYearMonth } from "@/lib/dates";
import { useBudgets } from "./hooks";
import { BudgetCard } from "./BudgetCard";
import { BudgetFormDialog } from "./BudgetFormDialog";

export function BudgetsPage() {
  const [month, setMonth] = useState(currentYearMonth());
  const [formOpen, setFormOpen] = useState(false);
  const [editingBudget, setEditingBudget] = useState<Budget | undefined>(undefined);

  const { data: budgets, isPending, isError } = useBudgets(month);
  const { data: categories } = useCategories();
  const categoriesById = useMemo(() => new Map((categories ?? []).map((c) => [c.id, c])), [categories]);

  function openCreate() {
    setEditingBudget(undefined);
    setFormOpen(true);
  }

  function openEdit(budget: Budget) {
    setEditingBudget(budget);
    setFormOpen(true);
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-xl font-semibold tracking-tight">Orçamento</h1>
        <div className="flex items-center gap-3">
          <MonthSelector value={month} onChange={setMonth} />
          <Button onClick={openCreate}>Novo orçamento</Button>
        </div>
      </div>

      {isPending && <p className="text-sm text-muted-foreground">Carregando orçamentos…</p>}

      {isError && (
        <p className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
          Não foi possível carregar os orçamentos. Tente recarregar a página.
        </p>
      )}

      {budgets && budgets.length === 0 && (
        <div className="rounded-xl border border-dashed border-border p-8 text-center text-sm text-muted-foreground">
          Nenhum orçamento planejado pra esse mês ainda.
        </div>
      )}

      {budgets && budgets.length > 0 && (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {budgets.map((budget) => (
            <BudgetCard key={budget.id} budget={budget} category={categoriesById.get(budget.categoryId)} onEdit={openEdit} />
          ))}
        </div>
      )}

      <BudgetFormDialog
        open={formOpen}
        onOpenChange={setFormOpen}
        budget={editingBudget}
        category={editingBudget ? categoriesById.get(editingBudget.categoryId) : undefined}
        defaultMonth={month}
      />
    </div>
  );
}
