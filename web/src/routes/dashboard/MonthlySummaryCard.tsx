import { CalendarCheck2, Sparkles } from "lucide-react";
import { CategoryIcon } from "@/components/CategoryIcon";
import { EmptyState } from "@/components/EmptyState";
import { Skeleton } from "@/components/ui/skeleton";
import { formatYearMonth } from "@/lib/dates";
import { formatMoney } from "@/lib/money";
import { useMonthlySummary } from "./insights-hooks";

/**
 * Resumo mensal automático (Fase 19): as frases vêm prontas do backend (regras e limiares
 * testados lá); aqui só a apresentação e as três maiores categorias.
 */
export function MonthlySummaryCard({ hidden }: { hidden: boolean }) {
  const { data, isPending } = useMonthlySummary();

  if (isPending || !data) {
    return <Skeleton className="h-56 rounded-xl" />;
  }
  if (!data.hasData) {
    return (
      <EmptyState
        icon={CalendarCheck2}
        title={`Nada lançado em ${formatYearMonth(data.month).toLowerCase()}`}
        description="O resumo aparece quando o mês tiver receitas ou despesas."
      />
    );
  }
  // Com saldos ocultos, as frases (que citam valores) dão lugar a um aviso.
  return (
    <div className="space-y-4">
      {hidden ? (
        <p className="text-sm text-muted-foreground">
          Valores ocultos. Mostre os saldos para ler o resumo.
        </p>
      ) : (
        <ul className="space-y-2.5">
          {data.headlines.map((line, i) => (
            <li key={line} className="flex gap-2 text-sm leading-snug">
              {i === 0 ? (
                <Sparkles className="mt-0.5 size-4 shrink-0 text-primary" />
              ) : (
                <span
                  className="mt-2 size-1.5 shrink-0 rounded-full bg-muted-foreground/50"
                  aria-hidden
                />
              )}
              <span className={i === 0 ? "font-semibold" : "text-muted-foreground"}>{line}</span>
            </li>
          ))}
        </ul>
      )}
      {data.topCategories.length > 0 && (
        <div className="space-y-2 border-t border-border/60 pt-3">
          <p className="text-xs font-medium text-muted-foreground">Onde o dinheiro foi</p>
          {data.topCategories.map((c) => (
            <div key={c.categoryId ?? "none"} className="flex items-center gap-2.5">
              <CategoryIcon icon={c.icon} color={c.color} className="size-7 [&_svg]:size-3.5" />
              <div className="min-w-0 flex-1">
                <div className="flex justify-between gap-2 text-sm">
                  <span className="truncate">{c.name}</span>
                  <span className="num font-medium">{hidden ? "•••" : formatMoney(c.total)}</span>
                </div>
                <div className="mt-1 h-1.5 overflow-hidden rounded-full bg-muted">
                  <div
                    className="h-full rounded-full"
                    style={{
                      width: `${Math.min(c.share ?? 0, 100)}%`,
                      backgroundColor: c.color ?? "var(--chart-expense)",
                    }}
                  />
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
