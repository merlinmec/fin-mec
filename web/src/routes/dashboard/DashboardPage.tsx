import { useState, type ReactNode } from "react";
import { Link } from "react-router-dom";
import { ArrowDownRight, ArrowUpRight, Clock, Eye, EyeOff, TrendingUp } from "lucide-react";
import { useAuth } from "@/auth/auth-context";
import { MonthSelector } from "@/components/MonthSelector";
import { CashFlowChart } from "@/components/charts/CashFlowChart";
import { BalanceChart } from "@/components/charts/BalanceChart";
import { Panel } from "@/components/ui/panel";
import { Skeleton } from "@/components/ui/skeleton";
import { useBalanceVisibility } from "@/hooks/useBalanceVisibility";
import { useCashFlow } from "@/hooks/useFeatureData";
import { currentYearMonth, formatYearMonth, shiftYearMonth } from "@/lib/dates";
import { formatMoney } from "@/lib/money";
import { cn } from "@/lib/utils";
import { useDashboard } from "./hooks";
import { AccountBalancesCard } from "./AccountBalancesCard";
import { ExpensesByCategoryChart } from "./ExpensesByCategoryChart";
import { UpcomingBillsCard } from "./UpcomingBillsCard";
import { BudgetsSummaryCard } from "./BudgetsSummaryCard";
import { PendingTransactionsCard } from "./PendingTransactionsCard";
import { GoalsSummaryCard } from "./GoalsSummaryCard";
import { ForecastCard } from "./ForecastCard";
import { MonthlySummaryCard } from "./MonthlySummaryCard";
import { monthlySummaryTitle, useMonthlySummary } from "./insights-hooks";

function greeting(): string {
  const hour = new Date().getHours();
  if (hour < 12) return "Bom dia";
  if (hour < 18) return "Boa tarde";
  return "Boa noite";
}

function seeAll(to: string, label = "Ver tudo") {
  return (
    <Link to={to} className="text-xs font-semibold text-primary hover:underline">
      {label}
    </Link>
  );
}

export function DashboardPage() {
  const { user } = useAuth();
  const [month, setMonth] = useState(currentYearMonth());
  const { data, isPending, isError } = useDashboard(month);
  const cashFlow = useCashFlow({ from: shiftYearMonth(month, -5), to: month });
  const { hidden, toggle, mask } = useBalanceVisibility();
  const name = user?.email.split("@")[0];

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <p className="text-sm text-muted-foreground">
            {greeting()}
            {name ? `, ${name}` : ""}
          </p>
          <h1 className="text-2xl font-bold tracking-tight">
            Visão geral de {formatYearMonth(month).toLowerCase()}
          </h1>
        </div>
        <MonthSelector value={month} onChange={setMonth} />
      </div>

      {isError && (
        <p className="rounded-xl bg-destructive/10 px-4 py-3 text-sm text-destructive">
          Não foi possível carregar o dashboard. Tente recarregar a página.
        </p>
      )}

      {isPending && <DashboardSkeleton />}

      {data && (
        <>
          <div className="grid gap-4 lg:grid-cols-3">
            <section className="relative overflow-hidden rounded-2xl border-l-4 border-primary bg-card p-5 shadow-card lg:col-span-2">
              <div className="grid gap-6 md:grid-cols-[1.1fr_1fr]">
                <div>
                  <div className="flex items-center gap-2 text-sm font-medium text-muted-foreground">
                    Saldo disponível hoje
                    <button
                      type="button"
                      onClick={toggle}
                      aria-label={hidden ? "Mostrar saldos" : "Ocultar saldos"}
                      className="rounded-full p-1 text-muted-foreground/80 transition-colors hover:bg-accent hover:text-foreground"
                    >
                      {hidden ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
                    </button>
                  </div>
                  <div
                    className={cn(
                      "num mt-1 text-[2.35rem] leading-none font-bold tracking-tight",
                      !hidden && data.totalAvailableBalance < 0 && "text-destructive",
                    )}
                  >
                    {mask(formatMoney(data.totalAvailableBalance))}
                  </div>
                  <dl className="mt-5 grid grid-cols-2 gap-4 text-sm">
                    <div>
                      <dt className="text-xs text-muted-foreground">Saldo contábil</dt>
                      <dd className="num mt-0.5 font-semibold">
                        {mask(formatMoney(data.totalLedgerBalance))}
                      </dd>
                    </div>
                    <div>
                      <dt className="flex items-center gap-1 text-xs text-muted-foreground">
                        <TrendingUp className="size-3.5" /> Previsão fim do mês
                      </dt>
                      <dd
                        className={cn(
                          "num mt-0.5 font-semibold",
                          !hidden && data.projectedBalance < 0 && "text-destructive",
                        )}
                      >
                        {mask(formatMoney(data.projectedBalance))}
                      </dd>
                    </div>
                  </dl>
                </div>

                <div className="grid grid-cols-2 gap-2.5">
                  <FlowTile
                    tone="in"
                    label="Recebido"
                    value={data.monthlyIncome}
                    icon={<ArrowDownRight className="size-4" />}
                  />
                  <FlowTile
                    tone="out"
                    label="Pago"
                    value={data.monthlyExpense}
                    icon={<ArrowUpRight className="size-4" />}
                  />
                  <FlowTile
                    tone="in"
                    pending
                    label="A receber"
                    value={data.pendingIncome}
                    icon={<Clock className="size-4" />}
                  />
                  <FlowTile
                    tone="out"
                    pending
                    label="A pagar"
                    value={data.pendingExpense}
                    icon={<Clock className="size-4" />}
                  />
                </div>
              </div>
              <p className="mt-4 border-t border-border/60 pt-3 text-xs text-muted-foreground">
                Previsão = saldo disponível − contas a pagar em aberto + lançamentos previstos
                (inclusive fixos) até o fim de {formatYearMonth(data.referenceMonth).toLowerCase()}.
              </p>
            </section>

            <Panel title="Minhas contas" action={seeAll("/contas", "Gerenciar")}>
              <AccountBalancesCard balances={data.accountBalances} hidden={hidden} />
            </Panel>
          </div>

          <div className="grid gap-4 lg:grid-cols-3">
            <Panel
              className="lg:col-span-2"
              title="Previsão de saldo"
              description="Dia a dia, com o que já está previsto"
            >
              <ForecastCard hidden={hidden} />
            </Panel>
            <SummaryPanel hidden={hidden} />
          </div>

          <div className="grid gap-4 lg:grid-cols-3">
            <Panel
              className="lg:col-span-2"
              title="Receitas x despesas"
              description={`Últimos 6 meses até ${formatYearMonth(month).toLowerCase()}`}
              action={seeAll("/relatorios", "Relatórios")}
            >
              {cashFlow.data ? (
                <CashFlowChart months={cashFlow.data.months} />
              ) : (
                <Skeleton className="h-[270px] rounded-xl" />
              )}
            </Panel>
            <Panel
              title="Gastos por categoria"
              description="Efetivados no mês"
              action={seeAll("/relatorios")}
            >
              <ExpensesByCategoryChart expenses={data.expensesByCategory} />
            </Panel>
          </div>

          <div className="grid gap-4 lg:grid-cols-3">
            <Panel
              title="A efetivar"
              description="Lançamentos previstos, inclusive dos fixos"
              action={seeAll("/lancamentos?status=PENDING")}
            >
              <PendingTransactionsCard />
            </Panel>
            <Panel title="Próximos vencimentos" action={seeAll("/contas-a-pagar")}>
              <UpcomingBillsCard bills={data.upcomingBills} />
            </Panel>
            <Panel title="Evolução do saldo" description="Saldo no fim de cada mês">
              {cashFlow.data ? (
                <BalanceChart months={cashFlow.data.months} hidden={hidden} height={180} />
              ) : (
                <Skeleton className="h-[210px] rounded-xl" />
              )}
            </Panel>
          </div>

          <div className="grid gap-4 lg:grid-cols-2">
            <Panel title="Orçamento do mês" action={seeAll("/orcamento")}>
              <BudgetsSummaryCard budgets={data.budgets} />
            </Panel>
            <Panel title="Metas" action={seeAll("/metas")}>
              <GoalsSummaryCard />
            </Panel>
          </div>
        </>
      )}
    </div>
  );
}

function SummaryPanel({ hidden }: { hidden: boolean }) {
  const { data } = useMonthlySummary();
  return (
    <Panel title={monthlySummaryTitle(data?.month)} description="Último mês fechado">
      <MonthlySummaryCard hidden={hidden} />
    </Panel>
  );
}

function FlowTile({
  label,
  value,
  tone,
  pending = false,
  icon,
}: {
  label: string;
  value: number;
  tone: "in" | "out";
  pending?: boolean;
  icon: ReactNode;
}) {
  return (
    <div
      className={cn(
        "rounded-xl p-3",
        pending ? "border border-dashed border-border bg-transparent" : "bg-surface-2",
      )}
    >
      <div
        className={cn(
          "flex items-center gap-1.5 text-xs font-medium",
          tone === "in" ? "text-success" : "text-destructive",
        )}
      >
        {icon}
        <span className={pending ? "text-muted-foreground" : undefined}>{label}</span>
      </div>
      <div className={cn("num mt-1 text-base font-bold", pending && "text-muted-foreground")}>
        {formatMoney(value)}
      </div>
    </div>
  );
}

function DashboardSkeleton() {
  return (
    <div className="space-y-4">
      <div className="grid gap-4 lg:grid-cols-3">
        <Skeleton className="h-60 rounded-2xl lg:col-span-2" />
        <Skeleton className="h-60 rounded-2xl" />
      </div>
      <div className="grid gap-4 lg:grid-cols-3">
        <Skeleton className="h-80 rounded-2xl lg:col-span-2" />
        <Skeleton className="h-80 rounded-2xl" />
      </div>
    </div>
  );
}
