import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactElement } from "react";
import { ApiError } from "@/api/client";
import type { Transaction } from "@/api/transactions";
import { currentYearMonth, todayIso } from "@/lib/dates";
import { TransactionFormDialog } from "./TransactionFormDialog";

vi.mock("@/api/transactions", async () => {
  const actual = await vi.importActual<typeof import("@/api/transactions")>("@/api/transactions");
  return { ...actual, createTransaction: vi.fn(), updateTransaction: vi.fn() };
});
vi.mock("@/api/accounts", async () => {
  const actual = await vi.importActual<typeof import("@/api/accounts")>("@/api/accounts");
  return { ...actual, listAccounts: vi.fn() };
});
vi.mock("@/api/categories", async () => {
  const actual = await vi.importActual<typeof import("@/api/categories")>("@/api/categories");
  return { ...actual, listCategories: vi.fn() };
});
vi.mock("@/api/tags", async () => {
  const actual = await vi.importActual<typeof import("@/api/tags")>("@/api/tags");
  return { ...actual, listTags: vi.fn() };
});

import { listAccounts } from "@/api/accounts";
import { listCategories } from "@/api/categories";
import { listTags } from "@/api/tags";
import { createTransaction, updateTransaction } from "@/api/transactions";

const ACCOUNT_ID = "conta-1";

const occurrence: Transaction = {
  id: "tx-1",
  accountId: ACCOUNT_ID,
  categoryId: null,
  type: "EXPENSE",
  amount: 120,
  description: "Academia",
  transactionDate: "2026-10-05",
  competenceMonth: "2026-10",
  status: "PENDING",
  transferPairId: null,
  transferDirection: null,
  installmentNumber: null,
  installmentTotal: null,
  installmentGroupId: null,
  recurrenceRule: "MONTHLY",
  recurrenceSeriesId: "serie-1",
  recurrenceIndex: 3,
  tagIds: [],
  createdBy: null,
  attachmentCount: 0,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

function renderWithClient(ui: ReactElement) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
}

describe("TransactionFormDialog", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listAccounts).mockResolvedValue([
      {
        id: ACCOUNT_ID,
        name: "Conta Corrente",
        type: "CHECKING",
        initialBalance: 0,
        currency: "BRL",
        archived: false,
        createdAt: "2026-01-01T00:00:00Z",
        updatedAt: "2026-01-01T00:00:00Z",
      },
    ] as Awaited<ReturnType<typeof listAccounts>>);
    vi.mocked(listCategories).mockResolvedValue([]);
    vi.mocked(listTags).mockResolvedValue([]);
    vi.mocked(createTransaction).mockResolvedValue({ ...occurrence, id: "novo" });
    vi.mocked(updateTransaction).mockResolvedValue(occurrence);
  });

  it("cria uma despesa paga com data de hoje e competencia do mes atual", async () => {
    const user = userEvent.setup();
    const onOpenChange = vi.fn();
    renderWithClient(
      <TransactionFormDialog open onOpenChange={onOpenChange} defaultAccountId={ACCOUNT_ID} />,
    );

    await user.type(screen.getByLabelText("Valor"), "45.90");
    await user.type(screen.getByLabelText("Descrição"), "Mercado");
    await user.click(screen.getByRole("button", { name: "Salvar" }));

    await waitFor(() =>
      expect(createTransaction).toHaveBeenCalledWith({
        accountId: ACCOUNT_ID,
        categoryId: undefined,
        type: "EXPENSE",
        amount: 45.9,
        description: "Mercado",
        transactionDate: todayIso(),
        competenceMonth: currentYearMonth(),
        status: "POSTED",
        recurrenceRule: undefined,
        recurrenceEndDate: undefined,
        tagIds: [],
      }),
    );
    await waitFor(() => expect(onOpenChange).toHaveBeenCalledWith(false));
  });

  it("muda para receita e acompanha a competencia quando a data muda", async () => {
    const user = userEvent.setup();
    renderWithClient(
      <TransactionFormDialog open onOpenChange={vi.fn()} defaultAccountId={ACCOUNT_ID} />,
    );

    await user.click(screen.getByRole("radio", { name: "Receita" }));
    await user.type(screen.getByLabelText("Valor"), "5000");
    await user.type(screen.getByLabelText("Descrição"), "Salário");
    await user.clear(screen.getByLabelText("Data"));
    await user.type(screen.getByLabelText("Data"), "2026-03-31");
    await user.click(screen.getByRole("button", { name: "Salvar" }));

    await waitFor(() =>
      expect(createTransaction).toHaveBeenCalledWith(
        expect.objectContaining({
          type: "INCOME",
          transactionDate: "2026-03-31",
          competenceMonth: "2026-03",
        }),
      ),
    );
  });

  it("troca entre despesa e receita pelas setas, como um radio group", async () => {
    const user = userEvent.setup();
    renderWithClient(
      <TransactionFormDialog open onOpenChange={vi.fn()} defaultAccountId={ACCOUNT_ID} />,
    );
    const expense = screen.getByRole("radio", { name: "Despesa" });
    const income = screen.getByRole("radio", { name: "Receita" });
    // Uma parada só de Tab para o grupo: a opção marcada.
    expect(expense).toHaveAttribute("tabindex", "0");
    expect(income).toHaveAttribute("tabindex", "-1");

    expense.focus();
    await user.keyboard("{ArrowRight}");

    expect(income).toHaveFocus();
    expect(income).toHaveAttribute("aria-checked", "true");

    await user.keyboard("{ArrowRight}");
    expect(expense).toHaveFocus();
    expect(expense).toHaveAttribute("aria-checked", "true");
  });

  it("envia regra e data final so quando o lancamento repete", async () => {
    const user = userEvent.setup();
    renderWithClient(
      <TransactionFormDialog open onOpenChange={vi.fn()} defaultAccountId={ACCOUNT_ID} />,
    );

    await user.type(screen.getByLabelText("Valor"), "120");
    await user.type(screen.getByLabelText("Descrição"), "Academia");
    await user.selectOptions(screen.getByLabelText("Repetir"), "MONTHLY");
    await user.type(screen.getByLabelText("Até (opcional)"), "2027-06-30");
    await user.click(screen.getByRole("button", { name: "Salvar" }));

    await waitFor(() =>
      expect(createTransaction).toHaveBeenCalledWith(
        expect.objectContaining({ recurrenceRule: "MONTHLY", recurrenceEndDate: "2027-06-30" }),
      ),
    );
  });

  it("valida conta, valor e descricao antes de chamar o backend", async () => {
    const user = userEvent.setup();
    renderWithClient(<TransactionFormDialog open onOpenChange={vi.fn()} />);

    await user.click(screen.getByRole("button", { name: "Salvar" }));

    expect(await screen.findByText("Informe um valor válido")).toBeInTheDocument();
    // O placeholder do seletor de conta tem o mesmo texto da mensagem de erro: 2 = placeholder + erro.
    expect(screen.getAllByText("Selecione uma conta")).toHaveLength(2);
    expect(screen.getByText("Informe uma descrição")).toBeInTheDocument();
    expect(createTransaction).not.toHaveBeenCalled();

    // Leitor de tela: o campo anuncia o motivo do erro, não só "inválido".
    expect(screen.getByLabelText("Valor")).toHaveAccessibleDescription("Informe um valor válido");
    expect(screen.getByLabelText("Descrição")).toHaveAccessibleDescription("Informe uma descrição");
    expect(screen.getByLabelText("Conta")).toHaveAccessibleDescription("Selecione uma conta");
    expect(screen.getByLabelText("Conta")).toHaveAttribute("aria-invalid", "true");
  });

  it("mostra o erro do backend e mantem o dialog aberto", async () => {
    const user = userEvent.setup();
    const onOpenChange = vi.fn();
    vi.mocked(createTransaction).mockRejectedValue(
      new ApiError(422, { detail: "Conta arquivada não aceita lançamentos" }),
    );
    renderWithClient(
      <TransactionFormDialog open onOpenChange={onOpenChange} defaultAccountId={ACCOUNT_ID} />,
    );

    await user.type(screen.getByLabelText("Valor"), "10");
    await user.type(screen.getByLabelText("Descrição"), "Café");
    await user.click(screen.getByRole("button", { name: "Salvar" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Conta arquivada não aceita lançamentos",
    );
    expect(onOpenChange).not.toHaveBeenCalled();
  });

  it("numa ocorrencia de lancamento fixo, oferece salvar este e os proximos", async () => {
    const user = userEvent.setup();
    renderWithClient(
      <TransactionFormDialog open onOpenChange={vi.fn()} transaction={occurrence} />,
    );

    expect(screen.getByText("Lançamento fixo mensal")).toBeInTheDocument();
    expect(screen.queryByLabelText("Repetir")).not.toBeInTheDocument();

    await user.clear(screen.getByLabelText("Valor"));
    await user.type(screen.getByLabelText("Valor"), "130");
    await user.click(screen.getByRole("button", { name: "Salvar este e os próximos" }));

    await waitFor(() =>
      expect(updateTransaction).toHaveBeenCalledWith(
        "tx-1",
        expect.objectContaining({ amount: 130, status: "PENDING", recurrenceRule: "MONTHLY" }),
        "THIS_AND_FUTURE",
      ),
    );
  });
});
