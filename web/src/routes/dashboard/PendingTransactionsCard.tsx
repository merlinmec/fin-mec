import { useQuery } from "@tanstack/react-query";
import { Check, Repeat } from "lucide-react";
import { searchTransactions } from "@/api/transactions";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useConfirmTransaction } from "@/routes/transactions/hooks";
import { addDaysIso, daysUntil, formatDate, todayIso } from "@/lib/dates";
import { formatMoney } from "@/lib/money";
import { cn } from "@/lib/utils";

const WINDOW_DAYS = 15;

function dueLabel(isoDate: string): string {
  const days = daysUntil(isoDate);
  if (days < 0) return `atrasado ${-days} dia${days === -1 ? "" : "s"}`;
  if (days === 0) return "hoje";
  if (days === 1) return "amanhã";
  return `em ${days} dias`;
}

/**
 * Lançamentos previstos até 15 dias à frente (e os atrasados): é onde as
 * ocorrências dos fixos aparecem para o usuário efetivar com um clique quando
 * o dinheiro de fato entrou ou saiu.
 */
export function PendingTransactionsCard() {
  const { data, isPending } = useQuery({
    queryKey: ["transactions", "pending-window"],
    queryFn: () => searchTransactions({ status: "PENDING", to: addDaysIso(WINDOW_DAYS), size: 6 }),
  });
  const confirm = useConfirmTransaction();

  if (isPending) return <Skeleton className="h-40 rounded-xl" />;
  const items = [...(data?.content ?? [])].sort((a, b) =>
    a.transactionDate.localeCompare(b.transactionDate),
  );

  if (items.length === 0) {
    return (
      <p className="text-sm text-muted-foreground">
        Nada previsto para os próximos {WINDOW_DAYS} dias. Lançamentos fixos (salário, aluguel,
        assinaturas) aparecem aqui para você confirmar.
      </p>
    );
  }

  const today = todayIso();
  return (
    <ul className="-mx-1 divide-y divide-border/60">
      {items.map((t) => {
        const outflow = t.type === "EXPENSE" || t.transferDirection === "OUT";
        const late = t.transactionDate < today;
        return (
          <li key={t.id} className="flex items-center gap-3 px-1 py-2.5 first:pt-0 last:pb-0">
            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-1.5 truncate text-sm font-medium">
                {t.recurrenceSeriesId && (
                  <Repeat className="size-3.5 shrink-0 text-muted-foreground" aria-label="Fixo" />
                )}
                <span className="truncate">{t.description}</span>
              </div>
              <div
                className={cn(
                  "text-xs text-muted-foreground",
                  late && "font-medium text-destructive",
                )}
              >
                {formatDate(t.transactionDate)} · {dueLabel(t.transactionDate)}
              </div>
            </div>
            <span
              className={cn(
                "num text-sm font-semibold",
                outflow ? "text-destructive" : "text-success",
              )}
            >
              {outflow ? "−" : "+"}
              {formatMoney(t.amount)}
            </span>
            <Button
              variant="outline"
              size="icon"
              className="size-8 shrink-0 rounded-full"
              disabled={confirm.isPending}
              onClick={() => confirm.mutate(t.id)}
              aria-label={`Efetivar ${t.description}`}
              title={outflow ? "Marcar como pago" : "Marcar como recebido"}
            >
              <Check className="size-4" />
            </Button>
          </li>
        );
      })}
    </ul>
  );
}
