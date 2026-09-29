import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { AlertTriangle, TrendingDown } from "lucide-react";
import { getForecast, type ForecastPoint } from "@/api/insights";
import { ForecastChart } from "@/components/charts/ForecastChart";
import { formatDayMonth } from "@/lib/chart-format";
import { Skeleton } from "@/components/ui/skeleton";
import { formatMoney } from "@/lib/money";
import { cn } from "@/lib/utils";

const HORIZONS = [30, 60, 90] as const;
type Horizon = (typeof HORIZONS)[number];

/**
 * Previsão de saldo (Fase 19). Busca 90 dias uma vez e recorta 30/60 no cliente — a curva é a
 * mesma, só o horizonte muda, então não vale uma ida ao servidor por clique.
 */
export function ForecastCard({ hidden }: { hidden: boolean }) {
  const [horizon, setHorizon] = useState<Horizon>(30);
  const { data, isPending, isError } = useQuery({
    queryKey: ["insights", "forecast"],
    queryFn: () => getForecast(90),
  });

  const view = useMemo(() => {
    if (!data) return null;
    const points = data.points.slice(0, horizon + 1);
    const lowest = points.reduce<ForecastPoint>(
      (low, p) => (p.balance < low.balance ? p : low),
      points[0],
    );
    const firstNegative = points.find((p) => p.balance < 0) ?? null;
    return { points, lowest, firstNegative, end: points[points.length - 1] };
  }, [data, horizon]);

  const money = (v: number) => (hidden ? "••••••" : formatMoney(v));

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="text-xs text-muted-foreground">Saldo previsto em {horizon} dias</p>
          {view ? (
            <p
              className={cn(
                "num text-2xl font-bold tracking-tight",
                !hidden && view.end.balance < 0 && "text-destructive",
              )}
            >
              {money(view.end.balance)}
            </p>
          ) : (
            <Skeleton className="mt-1 h-8 w-40" />
          )}
        </div>
        <div
          className="inline-flex rounded-lg bg-muted p-0.5 text-xs font-medium"
          role="group"
          aria-label="Horizonte da previsão"
        >
          {HORIZONS.map((h) => (
            <button
              key={h}
              type="button"
              onClick={() => setHorizon(h)}
              aria-pressed={horizon === h}
              className={cn(
                "rounded-md px-2.5 py-1 transition-colors",
                horizon === h
                  ? "bg-card text-foreground shadow-card"
                  : "text-muted-foreground hover:text-foreground",
              )}
            >
              {h} dias
            </button>
          ))}
        </div>
      </div>

      {view &&
        (view.firstNegative ? (
          <p
            role="status"
            className="flex items-start gap-2 rounded-lg bg-destructive/10 px-3 py-2 text-sm text-destructive"
          >
            <AlertTriangle className="mt-0.5 size-4 shrink-0" />
            <span>
              O saldo fica negativo em <strong>{formatDayMonth(view.firstNegative.date)}</strong> e
              chega a {money(view.lowest.balance)} em {formatDayMonth(view.lowest.date)}. Dá tempo
              de adiar ou cortar algo.
            </span>
          </p>
        ) : (
          <p className="flex items-center gap-2 text-sm text-muted-foreground">
            <TrendingDown className="size-4 shrink-0" />
            Ponto mais baixo: {money(view.lowest.balance)} em {formatDayMonth(view.lowest.date)}
          </p>
        ))}

      {isError ? (
        <p className="text-sm text-muted-foreground">Não foi possível calcular a previsão agora.</p>
      ) : isPending || !view ? (
        <Skeleton className="h-[250px] rounded-xl" />
      ) : (
        <ForecastChart points={view.points} lowest={view.lowest} hidden={hidden} />
      )}
      <p className="text-xs text-muted-foreground">
        Considera lançamentos previstos e agendados, fixos, contas a pagar e faturas em aberto.
        Atrasados contam como hoje.
      </p>
    </div>
  );
}
