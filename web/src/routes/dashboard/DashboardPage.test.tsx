import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { ApiError } from "@/api/client";
import type { Dashboard } from "@/api/dashboard";
import { AuthContext } from "@/auth/auth-context";
import { currentYearMonth, shiftYearMonth } from "@/lib/dates";
import { formatMoney } from "@/lib/money";
import { DashboardPage } from "./DashboardPage";

vi.mock("@/api/dashboard", () => ({ getDashboard: vi.fn() }));
vi.mock("@/api/reports", async () => {
  const actual = await vi.importActual<typeof import("@/api/reports")>("@/api/reports");
  return { ...actual, getCashFlow: vi.fn().mockResolvedValue({ months: [] }) };
});
vi.mock("@/api/insights", async () => {
  const actual = await vi.importActual<typeof import("@/api/insights")>("@/api/insights");
  return { ...actual, getMonthlySummary: vi.fn().mockResolvedValue(null) };
});
// Cards que buscam os próprios dados e gráficos (Recharts não mede nada no jsdom) têm testes e
// endpoints próprios; aqui o foco é a composição da página a partir do /dashboard.
vi.mock("./ForecastCard", () => ({ ForecastCard: () => null }));
vi.mock("./MonthlySummaryCard", () => ({ MonthlySummaryCard: () => null }));
vi.mock("./PendingTransactionsCard", () => ({ PendingTransactionsCard: () => null }));
vi.mock("./GoalsSummaryCard", () => ({ GoalsSummaryCard: () => null }));
vi.mock("./ExpensesByCategoryChart", () => ({ ExpensesByCategoryChart: () => null }));
vi.mock("@/components/charts/CashFlowChart", () => ({ CashFlowChart: () => null }));
vi.mock("@/components/charts/BalanceChart", () => ({ BalanceChart: () => null }));

import { getDashboard } from "@/api/dashboard";

/** O Testing Library normaliza o espaço não separável do Intl só no texto do DOM, não no matcher. */
const money = (value: number) => formatMoney(value).replace(/\s/g, " ");

const dashboard: Dashboard = {
  referenceMonth: currentYearMonth(),
  accountBalances: [
    {
      accountId: "1",
      accountName: "Nubank",
      accountType: "CHECKING",
      ledgerBalance: 2300,
      availableBalance: 2500,
    },
  ],
  totalLedgerBalance: 2300,
  totalAvailableBalance: 2500,
  monthlyIncome: 6000,
  monthlyExpense: 3500,
  pendingIncome: 0,
  pendingExpense: 800,
  projectedBalance: -150.5,
  upcomingBills: [],
  expensesByCategory: [],
  budgets: [],
};

function renderDashboard() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <AuthContext.Provider
          value={
            {
              status: "authenticated",
              user: { id: "1", email: "joao@example.com", createdAt: "", mfaEnabled: false },
            } as React.ContextType<typeof AuthContext>
          }
        >
          <DashboardPage />
        </AuthContext.Provider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("DashboardPage", () => {
  beforeEach(() => {
    vi.mocked(getDashboard).mockResolvedValue(dashboard);
  });

  it("mostra os totais calculados pelo backend e destaca previsão negativa", async () => {
    renderDashboard();

    // Total disponível e o saldo da única conta.
    expect(await screen.findAllByText(money(2500))).toHaveLength(2);
    expect(screen.getByText("joao", { exact: false })).toBeInTheDocument();
    expect(getDashboard).toHaveBeenCalledWith(currentYearMonth());

    const projected = screen.getByText(money(-150.5));
    expect(projected).toHaveClass("text-destructive");
    expect(screen.getByText("Nubank")).toBeInTheDocument();
  });

  it("oculta os saldos ao clicar no olho", async () => {
    const user = userEvent.setup();
    renderDashboard();
    await screen.findAllByText(money(2500));

    await user.click(screen.getByRole("button", { name: "Ocultar saldos" }));

    try {
      expect(screen.queryByText(money(2500))).not.toBeInTheDocument();
      expect(screen.queryByText(money(2300))).not.toBeInTheDocument();
      expect(screen.queryByText(money(-150.5))).not.toBeInTheDocument();
      // Fluxos do mês também: receita e despesa revelam a vida financeira tanto quanto o saldo.
      for (const flow of [6000, 3500, 800]) {
        expect(screen.queryByText(money(flow))).not.toBeInTheDocument();
      }
      // Mascarado não pode continuar vermelho: a cor entregaria que a previsão é negativa.
      for (const masked of screen.getAllByText("••••••")) {
        expect(masked).not.toHaveClass("text-destructive");
      }
    } finally {
      // A preferência é um store global do módulo (e persistida): desfaz mesmo se algo acima
      // falhar, senão a falha vaza para os testes seguintes.
      await user.click(screen.getByRole("button", { name: "Mostrar saldos" }));
    }
    expect(screen.getAllByText(money(2500))).toHaveLength(2);
  });

  it("busca o mês anterior ao navegar no seletor de mês", async () => {
    const user = userEvent.setup();
    renderDashboard();
    await screen.findAllByText(money(2500));

    await user.click(screen.getByRole("button", { name: "Mês anterior" }));

    await waitFor(() =>
      expect(getDashboard).toHaveBeenCalledWith(shiftYearMonth(currentYearMonth(), -1)),
    );
  });

  it("mostra mensagem de erro quando o dashboard falha", async () => {
    vi.mocked(getDashboard).mockRejectedValue(new ApiError(500, null));
    renderDashboard();

    expect(
      await screen.findByText("Não foi possível carregar o dashboard. Tente recarregar a página."),
    ).toBeInTheDocument();
    expect(within(document.body).queryByText("Minhas contas")).not.toBeInTheDocument();
  });
});
