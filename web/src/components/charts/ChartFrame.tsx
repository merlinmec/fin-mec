import { useState, type ReactNode } from "react";
import { BarChart3, Table2 } from "lucide-react";
import { cn } from "@/lib/utils";

interface ChartFrameProps {
  /** Legenda com a identidade de cada série (nunca só a cor). */
  legend?: { label: string; colorVar: string }[];
  chart: ReactNode;
  table: ReactNode;
  className?: string;
}

/**
 * Moldura comum dos gráficos: legenda + alternância gráfico/tabela. A tabela
 * não é enfeite — a validação da paleta no tema escuro deixou a despesa com
 * contraste < 3:1 contra o card, e a regra da skill de dataviz é oferecer a
 * leitura em tabela nesse caso (e ela serve para leitor de tela sempre).
 */
export function ChartFrame({ legend, chart, table, className }: ChartFrameProps) {
  const [asTable, setAsTable] = useState(false);

  return (
    <div className={cn("space-y-3", className)}>
      <div className="flex items-center justify-between gap-3">
        <ul className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-muted-foreground">
          {legend?.map((item) => (
            <li key={item.label} className="flex items-center gap-1.5">
              <span
                className="size-2.5 rounded-[3px]"
                style={{ backgroundColor: `var(${item.colorVar})` }}
              />
              {item.label}
            </li>
          ))}
        </ul>
        <button
          type="button"
          onClick={() => setAsTable((v) => !v)}
          className="inline-flex items-center gap-1 rounded-md px-2 py-1 text-xs font-medium text-muted-foreground transition-colors hover:bg-accent hover:text-foreground"
          aria-pressed={asTable}
        >
          {asTable ? <BarChart3 className="size-3.5" /> : <Table2 className="size-3.5" />}
          {asTable ? "Ver gráfico" : "Ver tabela"}
        </button>
      </div>
      {asTable ? <div className="overflow-x-auto">{table}</div> : chart}
    </div>
  );
}

/** Tooltip visual padrão dos gráficos (Recharts renderiza o conteúdo que devolvemos). */
export function ChartTooltipBox({
  title,
  rows,
}: {
  title: string;
  rows: { label: string; value: string; colorVar?: string }[];
}) {
  return (
    <div className="min-w-40 rounded-lg border border-border bg-popover px-3 py-2 text-xs text-popover-foreground shadow-float">
      <div className="mb-1.5 font-semibold">{title}</div>
      <div className="space-y-1">
        {rows.map((row) => (
          <div key={row.label} className="flex items-center justify-between gap-4">
            <span className="flex items-center gap-1.5 text-muted-foreground">
              {row.colorVar && (
                <span
                  className="size-2 rounded-[2px]"
                  style={{ backgroundColor: `var(${row.colorVar})` }}
                />
              )}
              {row.label}
            </span>
            <span className="num font-medium">{row.value}</span>
          </div>
        ))}
      </div>
    </div>
  );
}
