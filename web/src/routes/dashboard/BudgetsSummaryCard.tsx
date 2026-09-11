import { Link } from "react-router-dom";
import type { Budget } from "@/api/budgets";
import { useCategories } from "@/hooks/useCategories";
import { CategoryIcon } from "@/components/CategoryIcon";
import { Progress } from "@/components/ui/progress";
import { formatMoney } from "@/lib/money";

interface BudgetsSummaryCardProps {
  budgets: Budget[];
}

function progressColor(percentage: number): string {
  if (percentage > 100) return "bg-destructive";
  if (percentage >= 80) return "bg-warning";
  return "bg-success";
}

/** Versao compacta do BudgetCard (FE-5) — mesmas cores de status, sem as acoes de editar/excluir. */
export function BudgetsSummaryCard({ budgets }: BudgetsSummaryCardProps) {
  const { data: categories } = useCategories();
  const categoriesById = new Map((categories ?? []).map((c) => [c.id, c]));

  if (budgets.length === 0) {
    return <p className="text-sm text-muted-foreground">Nenhum orçamento planejado pra esse mês.</p>;
  }

  return (
    <div className="space-y-3">
      {budgets.map((budget) => {
        const category = categoriesById.get(budget.categoryId);
        return (
          <div key={budget.id} className="space-y-1">
            <div className="flex items-center justify-between text-sm">
              <span className="flex items-center gap-1.5">
                <CategoryIcon icon={category?.icon} color={category?.color} className="size-4" />
                {category?.name ?? "Categoria"}
              </span>
              <span className="tabular-nums text-muted-foreground">
                {formatMoney(budget.spent)} / {formatMoney(budget.amount)}
              </span>
            </div>
            <Progress value={budget.percentageUsed} indicatorClassName={progressColor(budget.percentageUsed)} />
          </div>
        );
      })}
      <Link to="/orcamento" className="inline-block text-sm font-medium text-primary hover:underline">
        Ver orçamento completo
      </Link>
    </div>
  );
}
