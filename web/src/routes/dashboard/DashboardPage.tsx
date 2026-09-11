import { useState, type ReactNode } from "react";
import { MonthSelector } from "@/components/MonthSelector";
import { StatTile } from "@/components/ui/stat-tile";
import { currentYearMonth, formatYearMonth } from "@/lib/dates";
import { formatMoney } from "@/lib/money";
import { useDashboard } from "./hooks";
import { AccountBalancesCard } from "./AccountBalancesCard";
import { ExpensesByCategoryChart } from "./ExpensesByCategoryChart";
import { UpcomingBillsCard } from "./UpcomingBillsCard";
import { BudgetsSummaryCard } from "./BudgetsSummaryCard";

function DashboardCard({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="rounded-xl border border-border bg-card p-4">
      <h2 className="mb-3 text-sm font-medium text-muted-foreground">{title}</h2>
      {children}
    </section>
  );
}

export function DashboardPage() {
  const [month, setMonth] = useState(currentYearMonth());
  const { data, isPending, isError } = useDashboard(month);

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-xl font-semibold tracking-tight">Dashboard</h1>
        <MonthSelector value={month} onChange={setMonth} />
      </div>

      {isPending && <p className="text-sm text-muted-foreground">Carregando…</p>}

      {isError && (
        <p className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
          Não foi possível carregar o dashboard. Tente recarregar a página.
        </p>
      )}

      {data && (
        <>
          <div className="grid grid-cols-2 gap-4 lg:grid-cols-5">
            <StatTile label="Saldo disponível" value={formatMoney(data.totalAvailableBalance)} />
            <StatTile label="Saldo contábil" value={formatMoney(data.totalLedgerBalance)} />
            <StatTile label="Receitas do mês" value={formatMoney(data.monthlyIncome)} tone="positive" />
            <StatTile label="Despesas do mês" value={formatMoney(data.monthlyExpense)} tone="negative" />
            <StatTile
              label="Previsão de saldo"
              value={formatMoney(data.projectedBalance)}
              tone={data.projectedBalance < 0 ? "negative" : "default"}
              className="col-span-2 lg:col-span-1"
            />
          </div>
          <p className="text-xs text-muted-foreground">
            Previsão = saldo disponível menos as contas a pagar em aberto até o fim de{" "}
            {formatYearMonth(data.referenceMonth)}. Não considera lançamentos recorrentes (ainda são só metadado).
          </p>

          <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
            <DashboardCard title="Saldo por conta">
              <AccountBalancesCard balances={data.accountBalances} />
            </DashboardCard>
            <DashboardCard title="Próximos vencimentos">
              <UpcomingBillsCard bills={data.upcomingBills} />
            </DashboardCard>
            <DashboardCard title="Gastos por categoria">
              <ExpensesByCategoryChart expenses={data.expensesByCategory} />
            </DashboardCard>
            <DashboardCard title="Orçamento do mês">
              <BudgetsSummaryCard budgets={data.budgets} />
            </DashboardCard>
          </div>
        </>
      )}
    </div>
  );
}
