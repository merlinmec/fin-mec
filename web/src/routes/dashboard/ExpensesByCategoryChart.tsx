import { Link } from "react-router-dom";
import type { CategoryExpense } from "@/api/dashboard";
import { useCategories } from "@/hooks/useCategories";
import { CategoryIcon } from "@/components/CategoryIcon";
import { Progress } from "@/components/ui/progress";
import { formatMoney } from "@/lib/money";

interface ExpensesByCategoryChartProps {
  expenses: CategoryExpense[];
}

const MAX_ROWS = 8;

/**
 * Barra horizontal por categoria em vez de um grafico de biblioteca (Recharts
 * cogitado no plano original) — cada linha ja e um mark totalmente rotulado
 * (icone + nome + valor, nunca so cor), entao uma lib de grafico inteira so
 * pra isso seria peso de bundle sem ganho real. Cor da barra e a cor propria
 * da categoria (mesma usada em CategoryIcon em toda a app — identidade
 * consistente), mas o rotulo direto (nome + valor) e quem carrega a leitura
 * de verdade, nao a cor.
 */
export function ExpensesByCategoryChart({ expenses }: ExpensesByCategoryChartProps) {
  const { data: categories } = useCategories();
  const categoriesById = new Map((categories ?? []).map((c) => [c.id, c]));

  if (expenses.length === 0) {
    return <p className="text-sm text-muted-foreground">Sem despesas neste mês ainda.</p>;
  }

  const rows = expenses.slice(0, MAX_ROWS);
  const max = Math.max(...rows.map((e) => e.amount));

  return (
    <div className="space-y-3">
      {rows.map((expense) => {
        const category = categoriesById.get(expense.categoryId);
        const percentage = max > 0 ? (expense.amount / max) * 100 : 0;
        return (
          <Link
            key={expense.categoryId}
            to={`/lancamentos?categoryId=${expense.categoryId}`}
            className="block space-y-1 rounded-md -m-1 p-1 hover:bg-accent/50"
          >
            <div className="flex items-center justify-between text-sm">
              <span className="flex items-center gap-1.5">
                <CategoryIcon icon={category?.icon} color={category?.color} className="size-4" />
                {expense.categoryName}
              </span>
              <span className="tabular-nums text-muted-foreground">{formatMoney(expense.amount)}</span>
            </div>
            <Progress
              value={percentage}
              indicatorStyle={{ backgroundColor: category?.color ?? "var(--color-muted-foreground)" }}
            />
          </Link>
        );
      })}
    </div>
  );
}
