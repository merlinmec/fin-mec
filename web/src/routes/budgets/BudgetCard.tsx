import { useState } from "react";
import type { Budget } from "@/api/budgets";
import type { Category } from "@/api/categories";
import { CategoryIcon } from "@/components/CategoryIcon";
import { Button } from "@/components/ui/button";
import { Progress } from "@/components/ui/progress";
import { formatMoney } from "@/lib/money";
import { cn } from "@/lib/utils";
import { useDeleteBudget } from "./hooks";

interface BudgetCardProps {
  budget: Budget;
  category: Category | undefined;
  onEdit: (budget: Budget) => void;
}

function progressColor(percentage: number): string {
  if (percentage > 100) return "bg-destructive";
  if (percentage >= 80) return "bg-warning";
  return "bg-success";
}

export function BudgetCard({ budget, category, onEdit }: BudgetCardProps) {
  const [confirming, setConfirming] = useState(false);
  const deleteBudget = useDeleteBudget();
  const overBudget = budget.percentageUsed > 100;

  return (
    <div className="space-y-3 rounded-xl border border-border bg-card p-4">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <CategoryIcon icon={category?.icon} color={category?.color} />
          <span className="font-medium">{category?.name ?? "Categoria"}</span>
        </div>
        <span className={cn("text-sm font-medium tabular-nums", overBudget && "text-destructive")}>
          {budget.percentageUsed.toFixed(0)}%
        </span>
      </div>

      <Progress value={budget.percentageUsed} indicatorClassName={progressColor(budget.percentageUsed)} />

      <div className="flex items-center justify-between text-sm">
        <span className={cn("tabular-nums", overBudget ? "font-medium text-destructive" : "text-muted-foreground")}>
          {formatMoney(budget.spent)}
        </span>
        <span className="text-muted-foreground tabular-nums">de {formatMoney(budget.amount)}</span>
      </div>

      <div className="flex justify-end gap-2 pt-1">
        <Button variant="outline" size="sm" onClick={() => onEdit(budget)} disabled={confirming}>
          Editar
        </Button>
        {confirming ? (
          <>
            <Button
              variant="destructive"
              size="sm"
              disabled={deleteBudget.isPending}
              onClick={() => deleteBudget.mutate(budget.id, { onSettled: () => setConfirming(false) })}
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
    </div>
  );
}
