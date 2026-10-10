import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { ApiError } from "@/api/client";
import type { CashFlowReport, CategoryReport } from "@/api/reports";
import { useBalanceVisibility } from "@/hooks/useBalanceVisibility";
import { formatMoney } from "@/lib/money";
import { ReportsPage } from "./ReportsPage";

vi.mock("@/api/reports", async () => {
  const actual = await vi.importActual<typeof import("@/api/reports")>("@/api/reports");
  return { ...actual, getCashFlow: vi.fn(), getCategoryReport: vi.fn() };
});
// Recharts não mede nada no jsdom; os gráficos têm testes próprios.
vi.mock("@/components/charts/CashFlowChart", () => ({ CashFlowChart: () => null }));
vi.mock("@/components/charts/BalanceChart", () => ({ BalanceChart: () => null }));

import { getCashFlow, getCategoryReport } from "@/api/reports";

const money = (value: number) => formatMoney(value).replace(/\s/g, " ");

const cashFlow: CashFlowReport = {
  from: "2026-05",
  to: "2026-10",
  openingBalance: 1000,
  closingBalance: 4000,
  totalIncome: 36000,
  totalExpense: 33000,
  net: 3000,
  averageMonthlyExpense: 5500,
  savingsRate: 8.3,
  months: [],
};

const categoryReport: CategoryReport = {
  from: "2026-05",
  to: "2026-10",
  type: "EXPENSE",
  total: 12345,
  previousTotal: 10000,
  changePercent: 23.4,
  categories: [
    {
      categoryId: "c1",
      name: "Mercado",
      color: null,
      icon: null,
      total: 12345,
      share: 100,
      previousTotal: 10000,
      changePercent: 23.4,
      monthlyAverage: 2057.5,
      monthly: [],
    },
  ],
};

function VisibilityToggle() {
  const { hidden, toggle } = useBalanceVisibility();
  return <button onClick={toggle}>{hidden ? "mostrar" : "ocultar"}</button>;
}

function renderReports() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <VisibilityToggle />
        <ReportsPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("ReportsPage", () => {
  beforeEach(() => {
    vi.mocked(getCashFlow).mockResolvedValue(cashFlow);
    vi.mocked(getCategoryReport).mockResolvedValue(categoryReport);
  });

  it("mostra os totais do período e o ranking por categoria", async () => {
    renderReports();

    expect(await screen.findByText(money(36000))).toBeInTheDocument();
    expect(screen.getByText(money(3000))).toHaveClass("text-success");
    expect(await screen.findByText("Mercado")).toBeInTheDocument();
    expect(screen.getByText(`${money(12345)} no período`, { exact: false })).toBeInTheDocument();
  });

  it("avisa quando o ranking por categoria falha, em vez de carregar para sempre", async () => {
    vi.mocked(getCategoryReport).mockRejectedValue(new ApiError(500, null));
    renderReports();

    expect(
      await screen.findByText(
        "Não foi possível carregar o ranking por categoria. Tente recarregar a página.",
      ),
    ).toBeInTheDocument();
  });

  it("com os saldos ocultos, mascara valores mas mantém os percentuais", async () => {
    const user = userEvent.setup();
    renderReports();
    await screen.findByText("Mercado");

    await user.click(screen.getByRole("button", { name: "ocultar" }));

    try {
      for (const value of [36000, 33000, 3000, 5500, 2057.5]) {
        expect(screen.queryByText(money(value), { exact: false })).not.toBeInTheDocument();
      }
      expect(screen.queryByText(money(12345), { exact: false })).not.toBeInTheDocument();
      // Receitas/Despesas têm cor fixa por tipo; só a cor do Resultado entregaria o sinal.
      const result = screen.getByText("Resultado").nextElementSibling!;
      expect(result).toHaveTextContent("••••••");
      expect(result).not.toHaveClass("text-success");
      expect(result).not.toHaveClass("text-destructive");
      // Percentuais são relativos: não revelam quanto a pessoa ganha ou gasta.
      expect(screen.getAllByText("23,4%", { exact: false }).length).toBeGreaterThan(0);
    } finally {
      // Store global do módulo: desfaz mesmo se algo acima falhar.
      await user.click(screen.getByRole("button", { name: "mostrar" }));
    }
  });
});
