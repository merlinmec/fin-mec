import { useState } from "react";
import { Link } from "react-router-dom";
import { ArrowDownRight, ArrowUpRight, BarChart3, Minus } from "lucide-react";
import type { EntryType } from "@/api/transactions";
import type { CategoryReportLine } from "@/api/reports";
import { CashFlowChart } from "@/components/charts/CashFlowChart";
import { BalanceChart } from "@/components/charts/BalanceChart";
import { CategoryIcon } from "@/components/CategoryIcon";
import { EmptyState } from "@/components/EmptyState";
import { PageHeader, Panel } from "@/components/ui/panel";
import { Skeleton } from "@/components/ui/skeleton";
import { useBalanceVisibility } from "@/hooks/useBalanceVisibility";
import { useCashFlow, useCategoryReport } from "@/hooks/useFeatureData";
import { currentYearMonth, formatYearMonth, shiftYearMonth } from "@/lib/dates";
import { formatMoney } from "@/lib/money";
import { cn } from "@/lib/utils";

type Preset = "3" | "6" | "12" | "year";

const PRESETS: { value: Preset; label: string }[] = [
  { value: "3", label: "3 meses" },
  { value: "6", label: "6 meses" },
  { value: "12", label: "12 meses" },
  { value: "year", label: "Este ano" },
];

function rangeFor(preset: Preset): { from: string; to: string } {
  const to = currentYearMonth();
  if (preset === "year") return { from: `${to.slice(0, 4)}-01`, to };
  return { from: shiftYearMonth(to, -(Number(preset) - 1)), to };
}

const percent = new Intl.NumberFormat("pt-BR", { maximumFractionDigits: 1 });

export function ReportsPage() {
  const [preset, setPreset] = useState<Preset>("6");
  const [type, setType] = useState<EntryType>("EXPENSE");
  const range = rangeFor(preset);
  const cashFlow = useCashFlow(range);
  const categories = useCategoryReport(range, type);
  const { hidden } = useBalanceVisibility();
  const report = cashFlow.data;

  return (
    <div className="space-y-5">
      <PageHeader
        title="Relatórios"
        description={`${formatYearMonth(range.from)} a ${formatYearMonth(range.to).toLowerCase()}`}
        actions={
          <div
            className="grid grid-cols-4 gap-1 rounded-xl bg-muted p-1"
            role="radiogroup"
            aria-label="Período"
          >
            {PRESETS.map((p) => (
              <button
                key={p.value}
                type="button"
                role="radio"
                aria-checked={preset === p.value}
                onClick={() => setPreset(p.value)}
                className={cn(
                  "rounded-lg px-3 py-1.5 text-xs font-semibold whitespace-nowrap transition-all",
                  preset === p.value
                    ? "bg-card text-foreground shadow-card"
                    : "text-muted-foreground hover:text-foreground",
                )}
              >
                {p.label}
              </button>
            ))}
          </div>
        }
      />

      {cashFlow.isError && (
        <p className="rounded-xl bg-destructive/10 px-4 py-3 text-sm text-destructive">
          Não foi possível carregar o relatório.
        </p>
      )}

      {!report ? (
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
          {Array.from({ length: 5 }).map((_, i) => (
            <Skeleton key={i} className="h-24 rounded-2xl" />
          ))}
        </div>
      ) : (
        <dl className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
          <Kpi label="Receitas" value={formatMoney(report.totalIncome)} tone="positive" />
          <Kpi label="Despesas" value={formatMoney(report.totalExpense)} tone="negative" />
          <Kpi
            label="Resultado"
            value={formatMoney(report.net)}
            tone={report.net < 0 ? "negative" : "positive"}
          />
          <Kpi
            label="Taxa de poupança"
            value={report.savingsRate === null ? "—" : `${percent.format(report.savingsRate)}%`}
            hint="Quanto da receita sobrou"
          />
          <Kpi label="Despesa média/mês" value={formatMoney(report.averageMonthlyExpense)} />
        </dl>
      )}

      <div className="grid gap-4 lg:grid-cols-5">
        <Panel
          className="lg:col-span-3"
          title="Receitas x despesas por mês"
          description="Só o que já foi efetivado, por competência"
        >
          {report ? (
            <CashFlowChart months={report.months} height={300} />
          ) : (
            <Skeleton className="h-[330px] rounded-xl" />
          )}
        </Panel>
        <Panel
          className="lg:col-span-2"
          title="Evolução do saldo"
          description={
            report && !hidden
              ? `De ${formatMoney(report.openingBalance)} para ${formatMoney(report.closingBalance)}`
              : "Saldo contábil no fim de cada mês"
          }
        >
          {report ? (
            <BalanceChart months={report.months} height={300} hidden={hidden} />
          ) : (
            <Skeleton className="h-[330px] rounded-xl" />
          )}
        </Panel>
      </div>

      <Panel
        title={type === "EXPENSE" ? "Para onde foi o dinheiro" : "De onde veio o dinheiro"}
        description={
          categories.data
            ? `${formatMoney(categories.data.total)} no período${
                categories.data.changePercent !== null
                  ? ` · ${categories.data.changePercent >= 0 ? "+" : ""}${percent.format(categories.data.changePercent)}% vs. período anterior`
                  : ""
              }`
            : undefined
        }
        action={
          <div
            className="grid grid-cols-2 gap-1 rounded-lg bg-muted p-1"
            role="radiogroup"
            aria-label="Tipo"
          >
            {(["EXPENSE", "INCOME"] as EntryType[]).map((t) => (
              <button
                key={t}
                type="button"
                role="radio"
                aria-checked={type === t}
                onClick={() => setType(t)}
                className={cn(
                  "rounded-md px-3 py-1 text-xs font-semibold transition-all",
                  type === t
                    ? "bg-card text-foreground shadow-card"
                    : "text-muted-foreground hover:text-foreground",
                )}
              >
                {t === "EXPENSE" ? "Despesas" : "Receitas"}
              </button>
            ))}
          </div>
        }
      >
        {!categories.data ? (
          <Skeleton className="h-64 rounded-xl" />
        ) : categories.data.categories.length === 0 ? (
          <EmptyState
            icon={BarChart3}
            title="Sem movimentação no período"
            description="Os lançamentos efetivados aparecem aqui por categoria."
          />
        ) : (
          <CategoryRanking lines={categories.data.categories} type={type} />
        )}
      </Panel>
    </div>
  );
}

function Kpi({
  label,
  value,
  tone,
  hint,
}: {
  label: string;
  value: string;
  tone?: "positive" | "negative";
  hint?: string;
}) {
  return (
    <div className="rounded-2xl border border-border/60 bg-card p-4 shadow-card">
      <dt className="text-xs font-medium text-muted-foreground">{label}</dt>
      <dd
        className={cn(
          "num mt-1 text-xl font-bold tracking-tight",
          tone === "positive" && "text-success",
          tone === "negative" && "text-destructive",
        )}
      >
        {value}
      </dd>
      {hint && <p className="mt-0.5 text-[11px] text-muted-foreground">{hint}</p>}
    </div>
  );
}

/**
 * Ranking horizontal (barra = participação no total, vinda do backend). Cada
 * linha carrega nome + valor + % direto: a cor da categoria só reforça a
 * identidade, nunca é a única pista.
 */
function CategoryRanking({ lines, type }: { lines: CategoryReportLine[]; type: EntryType }) {
  return (
    <ul className="-mx-2 space-y-0.5">
      {lines.map((line) => {
        const to = line.categoryId ? `/lancamentos?categoryId=${line.categoryId}` : "/lancamentos";
        return (
          <li key={line.categoryId ?? "none"}>
            <Link
              to={to}
              className="grid grid-cols-[auto_1fr_auto] items-center gap-x-3 gap-y-1.5 rounded-xl px-2 py-2.5 transition-colors hover:bg-surface-2 sm:grid-cols-[auto_minmax(0,14rem)_1fr_8.5rem_4.5rem]"
            >
              <CategoryIcon icon={line.icon} color={line.color} className="size-8 [&_svg]:size-4" />
              <div className="min-w-0">
                <div className="truncate text-sm font-semibold">{line.name}</div>
                <div className="text-xs text-muted-foreground">
                  {percent.format(line.share)}% · média {formatMoney(line.monthlyAverage)}/mês
                </div>
              </div>
              <div className="col-span-3 row-start-2 h-2 overflow-hidden rounded-full bg-muted sm:col-span-1 sm:row-start-auto">
                <div
                  className="h-full rounded-full"
                  style={{
                    width: `${Math.max(2, Math.min(100, line.share))}%`,
                    backgroundColor: line.color ?? "var(--color-muted-foreground)",
                  }}
                />
              </div>
              <span className="num col-start-3 row-start-1 text-right text-sm font-bold sm:col-start-auto sm:row-start-auto">
                {formatMoney(line.total)}
              </span>
              <ChangeBadge value={line.changePercent} type={type} />
            </Link>
          </li>
        );
      })}
    </ul>
  );
}

/** Variação vs. período anterior. Em despesa, subir é ruim (vermelho); em receita, subir é bom. */
function ChangeBadge({ value, type }: { value: number | null; type: EntryType }) {
  if (value === null) {
    return (
      <span className="hidden w-20 text-right text-[11px] font-semibold text-muted-foreground sm:inline">
        novo
      </span>
    );
  }
  const up = value > 0;
  const flat = value === 0;
  const good = flat ? null : type === "EXPENSE" ? !up : up;
  const Icon = flat ? Minus : up ? ArrowUpRight : ArrowDownRight;
  return (
    <span
      className={cn(
        "hidden w-20 items-center justify-end gap-0.5 text-[11px] font-semibold sm:inline-flex",
        good === null ? "text-muted-foreground" : good ? "text-success" : "text-destructive",
      )}
      title="Variação contra o período anterior de mesmo tamanho"
    >
      <Icon className="size-3.5" />
      {percent.format(Math.abs(value))}%
    </span>
  );
}
