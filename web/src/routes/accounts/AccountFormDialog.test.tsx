import { describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactElement } from "react";
import { AccountFormDialog } from "./AccountFormDialog";

vi.mock("@/api/accounts", async () => {
  const actual = await vi.importActual<typeof import("@/api/accounts")>("@/api/accounts");
  return { ...actual, createAccount: vi.fn(), updateAccount: vi.fn(), deleteAccount: vi.fn(), listAccounts: vi.fn() };
});

import { createAccount } from "@/api/accounts";

function renderWithClient(ui: ReactElement) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
}

describe("AccountFormDialog — criar conta", () => {
  it("envia name/type/initialBalance ao submeter e fecha o dialog", async () => {
    const user = userEvent.setup();
    vi.mocked(createAccount).mockResolvedValue({
      id: "1",
      name: "Conta Corrente",
      type: "CHECKING",
      initialBalance: 1000,
      currency: "BRL",
      archived: false,
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-01T00:00:00Z",
    });
    const onOpenChange = vi.fn();

    renderWithClient(<AccountFormDialog open onOpenChange={onOpenChange} />);

    await user.type(screen.getByLabelText("Nome"), "Conta Corrente");
    await user.clear(screen.getByLabelText("Saldo inicial"));
    await user.type(screen.getByLabelText("Saldo inicial"), "1000");
    await user.click(screen.getByRole("button", { name: "Criar conta" }));

    await waitFor(() => {
      expect(createAccount).toHaveBeenCalledWith({
        name: "Conta Corrente",
        type: "CHECKING",
        initialBalance: 1000,
      });
    });
    await waitFor(() => expect(onOpenChange).toHaveBeenCalledWith(false));
  });

  it("nao chama o backend se o nome estiver vazio", async () => {
    const user = userEvent.setup();

    renderWithClient(<AccountFormDialog open onOpenChange={vi.fn()} />);

    await user.click(screen.getByRole("button", { name: "Criar conta" }));

    expect(await screen.findByText("Informe o nome")).toBeInTheDocument();
    expect(createAccount).not.toHaveBeenCalled();
  });
});
