import { useId } from "react";
import {
  Area,
  AreaChart,
  CartesianGrid,
  ReferenceDot,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import type { ForecastPoint } from "@/api/insights";
import { formatCompactMoney, formatDayMonth } from "@/lib/chart-format";
import { formatDate } from "@/lib/dates";
import { formatMoney } from "@/lib/money";
import { cn } from "@/lib/utils";
import { ChartFrame, ChartTooltipBox } from "./ChartFrame";

interface ForecastChartProps {
  points: ForecastPoint[];
  lowest: ForecastPoint;
  height?: number;
  hidden?: boolean;
}

/**
 * Curva de saldo previsto, dia a dia (série única: o título do card nomeia, sem legenda). Abaixo
 * de zero a linha e o preenchimento trocam para a cor de despesa — o "vai faltar dinheiro" não
 * depende de ler o eixo. O ponto mais baixo ganha marcador; o detalhe de cada dia vem no tooltip
 * e na tabela.
 */
export function ForecastChart({
  points,
  lowest,
  height = 220,
  hidden = false,
}: ForecastChartProps) {
  const id = useId().replace(/[^a-zA-Z0-9]/g, "");
  const max = Math.max(...points.map((p) => p.balance), 0);
  const min = Math.min(...points.map((p) => p.balance), 0);
  // Onde o zero cai no gradiente vertical (0 = topo, 1 = base).
  const zeroAt = max === min ? 1 : max / (max - min);
  const crossesZero = min < 0;
  const money = (v: number) => (hidden ? "••••••" : formatMoney(v));

  const splitStops = (opacityTop: number, opacityBottom: number) => (
    <>
      <stop offset="0%" stopColor="var(--chart-balance)" stopOpacity={opacityTop} />
      <stop
        offset={`${zeroAt * 100}%`}
        stopColor="var(--chart-balance)"
        stopOpacity={opacityBottom}
      />
      <stop
        offset={`${zeroAt * 100}%`}
        stopColor="var(--chart-expense)"
        stopOpacity={opacityBottom}
      />
      <stop offset="100%" stopColor="var(--chart-expense)" stopOpacity={opacityTop} />
    </>
  );

  return (
    <ChartFrame
      chart={
        <div style={{ height }} role="img" aria-label="Gráfico da previsão de saldo">
          <ResponsiveContainer width="100%" height="100%">
            <AreaChart data={points} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
              <defs>
                <linearGradient id={`fill-${id}`} x1="0" y1="0" x2="0" y2="1">
                  {splitStops(0.24, 0.02)}
                </linearGradient>
                <linearGradient id={`stroke-${id}`} x1="0" y1="0" x2="0" y2="1">
                  {splitStops(1, 1)}
                </linearGradient>
              </defs>
              <CartesianGrid vertical={false} stroke="var(--chart-grid)" />
              <XAxis
                dataKey="date"
                tickFormatter={formatDayMonth}
                tickLine={false}
                axisLine={false}
                minTickGap={28}
                tick={{ fill: "var(--chart-axis)", fontSize: 12 }}
                dy={6}
              />
              <YAxis
                tickFormatter={(v: number) => (hidden ? "•••" : formatCompactMoney(v))}
                tickLine={false}
                axisLine={false}
                width={52}
                tick={{ fill: "var(--chart-axis)", fontSize: 12 }}
              />
              {crossesZero && (
                <ReferenceLine y={0} stroke="var(--chart-axis)" strokeDasharray="4 4" />
              )}
              <Tooltip
                cursor={{ stroke: "var(--chart-axis)", strokeWidth: 1, strokeDasharray: "3 3" }}
                content={({ active, payload }) => {
                  const row = payload?.[0]?.payload as ForecastPoint | undefined;
                  if (!active || !row) return null;
                  const shown = row.events.slice(0, 4);
                  return (
                    <ChartTooltipBox
                      title={formatDate(row.date)}
                      rows={[
                        { label: "Saldo previsto", value: money(row.balance) },
                        ...shown.map((e) => ({
                          label: `${e.overdue ? "Atrasado · " : ""}${e.description}`.slice(0, 32),
                          value: hidden
                            ? "•••"
                            : `${e.amount >= 0 ? "+" : "−"}${formatMoney(Math.abs(e.amount))}`,
                        })),
                        ...(row.events.length > shown.length
                          ? [{ label: `+${row.events.length - shown.length} outros`, value: "" }]
                          : []),
                      ]}
                    />
                  );
                }}
              />
              <Area
                type="stepAfter"
                dataKey="balance"
                stroke={`url(#stroke-${id})`}
                strokeWidth={2}
                fill={`url(#fill-${id})`}
                dot={false}
                activeDot={{
                  r: 5,
                  strokeWidth: 2,
                  stroke: "var(--color-card)",
                  fill: "var(--chart-balance)",
                }}
              />
              <ReferenceDot
                x={lowest.date}
                y={lowest.balance}
                r={5}
                fill={lowest.balance < 0 ? "var(--chart-expense)" : "var(--chart-balance)"}
                stroke="var(--color-card)"
                strokeWidth={2}
              />
            </AreaChart>
          </ResponsiveContainer>
        </div>
      }
      table={
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-border text-left text-xs text-muted-foreground">
              <th className="py-2 pr-3 font-medium">Dia</th>
              <th className="py-2 pr-3 font-medium">O que acontece</th>
              <th className="py-2 text-right font-medium">Saldo previsto</th>
            </tr>
          </thead>
          <tbody>
            {points
              .filter((p, i) => i === 0 || p.events.length > 0)
              .map((p) => (
                <tr key={p.date} className="border-b border-border/60 align-top last:border-0">
                  <td className="py-2 pr-3 whitespace-nowrap">{formatDayMonth(p.date)}</td>
                  <td className="py-2 pr-3 text-muted-foreground">
                    {p.events.length === 0
                      ? "Hoje"
                      : p.events
                          .map(
                            (e) =>
                              `${e.description} (${hidden ? "•••" : `${e.amount >= 0 ? "+" : "−"}${formatMoney(Math.abs(e.amount))}`})`,
                          )
                          .join(", ")}
                  </td>
                  <td
                    className={cn(
                      "num py-2 text-right whitespace-nowrap",
                      !hidden && p.balance < 0 && "text-destructive",
                    )}
                  >
                    {money(p.balance)}
                  </td>
                </tr>
              ))}
          </tbody>
        </table>
      }
    />
  );
}
