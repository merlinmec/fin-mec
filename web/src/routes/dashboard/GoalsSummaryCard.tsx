import { Link } from "react-router-dom";
import { Target } from "lucide-react";
import { CategoryIcon } from "@/components/CategoryIcon";
import { Progress } from "@/components/ui/progress";
import { Skeleton } from "@/components/ui/skeleton";
import { useGoals } from "@/hooks/useFeatureData";
import { formatMoney } from "@/lib/money";

const MAX_GOALS = 4;

/** Metas ativas mais recentes com o progresso já calculado pelo backend. */
export function GoalsSummaryCard() {
  const { data: goals, isPending } = useGoals();
  if (isPending) return <Skeleton className="h-32 rounded-xl" />;

  if (!goals || goals.length === 0) {
    return (
      <div className="flex items-center gap-3 rounded-xl border border-dashed border-border p-4">
        <Target className="size-8 shrink-0 text-primary/70" />
        <div className="text-sm">
          <p className="font-medium">Crie sua primeira meta</p>
          <p className="text-muted-foreground">
            Reserva de emergência, viagem, troca de carro…{" "}
            <Link to="/metas" className="font-medium text-primary hover:underline">
              Começar
            </Link>
          </p>
        </div>
      </div>
    );
  }

  return (
    <ul className="space-y-3.5">
      {goals.slice(0, MAX_GOALS).map((goal) => (
        <li key={goal.id} className="space-y-1.5">
          <div className="flex flex-wrap items-center gap-x-2 gap-y-0.5 text-sm">
            <CategoryIcon
              icon={goal.icon ?? "piggy-bank"}
              color={goal.color ?? "var(--color-primary)"}
              className="size-6"
            />
            <span className="min-w-32 flex-1 truncate font-medium">{goal.name}</span>
            <span className="num text-xs text-muted-foreground">
              {formatMoney(goal.savedAmount)} de {formatMoney(goal.targetAmount)}
            </span>
          </div>
          <Progress
            value={goal.progressPercent}
            indicatorClassName={
              goal.status === "COMPLETED"
                ? "bg-success"
                : goal.status === "OVERDUE"
                  ? "bg-warning"
                  : undefined
            }
            indicatorStyle={
              goal.status === "ACTIVE"
                ? { backgroundColor: goal.color ?? "var(--color-primary)" }
                : undefined
            }
          />
        </li>
      ))}
    </ul>
  );
}
