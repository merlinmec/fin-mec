import { useId } from "react";
import {
  Area,
  AreaChart,
  CartesianGrid,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import type { CashFlowMonth } from "@/api/reports";
import { formatCompactMoney, formatShortMonth } from "@/lib/chart-format";
import { formatYearMonth } from "@/lib/dates";
import { formatMoney } from "@/lib/money";
import { cn } from "@/lib/utils";
import { ChartFrame, ChartTooltipBox } from "./ChartFrame";

interface BalanceChartProps {
  months: CashFlowMonth[];
  height?: number;
  hidden?: boolean;
}

/**
 * Evolução do saldo contábil no fim de cada mês (série única: o título nomeia,
 * sem caixa de legenda). Linha de 2px com preenchimento leve até a base;
 * linha de referência no zero quando a série cruza o negativo.
 */
export function BalanceChart({ months, height = 200, hidden = false }: BalanceChartProps) {
  // useId traz caracteres (":" ou "«»") que quebram a referência url(#id) do SVG.
  const gradientId = `balance-${useId().replace(/[^a-zA-Z0-9]/g, "")}`;
  const crossesZero = months.some((m) => m.closingBalance < 0);

  return (
    <ChartFrame
      chart={
        <div style={{ height }} role="img" aria-label="Gráfico da evolução do saldo">
          <ResponsiveContainer width="100%" height="100%">
            <AreaChart data={months} margin={{ top: 6, right: 6, bottom: 0, left: 0 }}>
              <defs>
                <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor="var(--chart-balance)" stopOpacity={0.22} />
                  <stop offset="100%" stopColor="var(--chart-balance)" stopOpacity={0} />
                </linearGradient>
              </defs>
              <CartesianGrid vertical={false} stroke="var(--chart-grid)" />
              <XAxis
                dataKey="month"
                tickFormatter={formatShortMonth}
                tickLine={false}
                axisLine={false}
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
                  const row = payload?.[0]?.payload as CashFlowMonth | undefined;
                  if (!active || !row) return null;
                  return (
                    <ChartTooltipBox
                      title={formatYearMonth(row.month)}
                      rows={[
                        {
                          label: "Saldo no fim do mês",
                          value: hidden ? "••••••" : formatMoney(row.closingBalance),
                        },
                      ]}
                    />
                  );
                }}
              />
              <Area
                type="monotone"
                dataKey="closingBalance"
                stroke="var(--chart-balance)"
                strokeWidth={2}
                fill={`url(#${gradientId})`}
                dot={false}
                activeDot={{
                  r: 5,
                  strokeWidth: 2,
                  stroke: "var(--color-card)",
                  fill: "var(--chart-balance)",
                }}
              />
            </AreaChart>
          </ResponsiveContainer>
        </div>
      }
      table={
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-border text-left text-xs text-muted-foreground">
              <th className="py-2 pr-3 font-medium">Mês</th>
              <th className="py-2 text-right font-medium">Saldo no fim do mês</th>
            </tr>
          </thead>
          <tbody>
            {months.map((m) => (
              <tr key={m.month} className="border-b border-border/60 last:border-0">
                <td className="py-2 pr-3">{formatYearMonth(m.month)}</td>
                <td
                  className={cn(
                    "num py-2 text-right",
                    !hidden && m.closingBalance < 0 && "text-destructive",
                  )}
                >
                  {hidden ? "••••••" : formatMoney(m.closingBalance)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      }
    />
  );
}
