import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { formatMoney } from "@/lib/money";
import { CashFlowChart } from "./CashFlowChart";

const money = (value: number) => formatMoney(value).replace(/\s/g, " ");
const months = [
  {
    month: "2026-09",
    income: 6000,
    expense: 7200,
    net: -1200,
    pendingIncome: 0,
    pendingExpense: 0,
    closingBalance: 300,
  },
];

async function showAsTable() {
  await userEvent.click(screen.getByRole("button", { name: /tabela/i }));
}

describe("CashFlowChart (visão em tabela)", () => {
  it("mostra receitas, despesas e resultado negativo em destaque", async () => {
    render(<CashFlowChart months={months} />);
    await showAsTable();

    expect(screen.getByText(money(6000))).toBeInTheDocument();
    expect(screen.getByText(money(7200))).toBeInTheDocument();
    expect(screen.getByText(money(-1200))).toHaveClass("text-destructive");
  });

  it("oculta os valores e a cor do resultado com os saldos ocultos", async () => {
    render(<CashFlowChart months={months} hidden />);
    await showAsTable();

    expect(screen.queryByText(money(6000))).not.toBeInTheDocument();
    expect(screen.queryByText(money(-1200))).not.toBeInTheDocument();
    for (const masked of screen.getAllByText("••••••")) {
      expect(masked).not.toHaveClass("text-destructive");
      expect(masked).not.toHaveClass("text-success");
    }
  });
});
