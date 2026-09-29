import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import type { CashFlowMonth } from "@/api/reports";
import { formatCompactMoney, formatShortMonth } from "@/lib/chart-format";
import { formatYearMonth } from "@/lib/dates";
import { formatMoney } from "@/lib/money";
import { cn } from "@/lib/utils";
import { ChartFrame, ChartTooltipBox } from "./ChartFrame";

interface CashFlowChartProps {
  months: CashFlowMonth[];
  height?: number;
}

const LEGEND = [
  { label: "Receitas", colorVar: "--chart-income" },
  { label: "Despesas", colorVar: "--chart-expense" },
];

/**
 * Receitas x despesas efetivadas por mês: barras agrupadas (comparação de
 * magnitude lado a lado), receita sempre à esquerda. O saldo acumulado vai em
 * outro gráfico (BalanceChart) — nunca num segundo eixo Y neste.
 */
export function CashFlowChart({ months, height = 240 }: CashFlowChartProps) {
  return (
    <ChartFrame
      legend={LEGEND}
      chart={
        <div style={{ height }} role="img" aria-label="Gráfico de receitas e despesas por mês">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart
              data={months}
              barGap={2}
              barCategoryGap="28%"
              margin={{ top: 4, right: 4, bottom: 0, left: 0 }}
            >
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
                tickFormatter={formatCompactMoney}
                tickLine={false}
                axisLine={false}
                width={52}
                tick={{ fill: "var(--chart-axis)", fontSize: 12 }}
              />
              <Tooltip
                cursor={{ fill: "var(--color-accent)", opacity: 0.6 }}
                content={({ active, payload }) => {
                  const row = payload?.[0]?.payload as CashFlowMonth | undefined;
                  if (!active || !row) return null;
                  return (
                    <ChartTooltipBox
                      title={formatYearMonth(row.month)}
                      rows={[
                        {
                          label: "Receitas",
                          value: formatMoney(row.income),
                          colorVar: "--chart-income",
                        },
                        {
                          label: "Despesas",
                          value: formatMoney(row.expense),
                          colorVar: "--chart-expense",
                        },
                        { label: "Resultado", value: formatMoney(row.net) },
                      ]}
                    />
                  );
                }}
              />
              <Bar
                dataKey="income"
                name="Receitas"
                fill="var(--chart-income)"
                radius={[4, 4, 0, 0]}
                maxBarSize={28}
              />
              <Bar
                dataKey="expense"
                name="Despesas"
                fill="var(--chart-expense)"
                radius={[4, 4, 0, 0]}
                maxBarSize={28}
              />
            </BarChart>
          </ResponsiveContainer>
        </div>
      }
      table={
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-border text-left text-xs text-muted-foreground">
              <th className="py-2 pr-3 font-medium">Mês</th>
              <th className="py-2 pr-3 text-right font-medium">Receitas</th>
              <th className="py-2 pr-3 text-right font-medium">Despesas</th>
              <th className="py-2 text-right font-medium">Resultado</th>
            </tr>
          </thead>
          <tbody>
            {months.map((m) => (
              <tr key={m.month} className="border-b border-border/60 last:border-0">
                <td className="py-2 pr-3">{formatYearMonth(m.month)}</td>
                <td className="num py-2 pr-3 text-right">{formatMoney(m.income)}</td>
                <td className="num py-2 pr-3 text-right">{formatMoney(m.expense)}</td>
                <td
                  className={cn(
                    "num py-2 text-right font-medium",
                    m.net < 0 ? "text-destructive" : "text-success",
                  )}
                >
                  {formatMoney(m.net)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      }
    />
  );
}
