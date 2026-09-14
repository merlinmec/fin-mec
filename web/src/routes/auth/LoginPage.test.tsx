import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { ApiError } from "@/api/client";
import { AuthProvider } from "@/auth/AuthProvider";
import { LoginPage } from "./LoginPage";

vi.mock("@/api/auth", () => ({
  fetchCurrentUser: vi.fn(),
  loginUser: vi.fn(),
  registerUser: vi.fn(),
  logoutUser: vi.fn(),
}));
vi.mock("@/api/csrf", () => ({
  bootstrapCsrf: vi.fn(),
}));

import { fetchCurrentUser, loginUser } from "@/api/auth";
import { bootstrapCsrf } from "@/api/csrf";

function renderLoginPage() {
  return render(
    <MemoryRouter initialEntries={["/login"]}>
      <AuthProvider>
        <LoginPage />
      </AuthProvider>
    </MemoryRouter>,
  );
}

describe("LoginPage", () => {
  beforeEach(() => {
    vi.mocked(bootstrapCsrf).mockResolvedValue(undefined);
    // Boot do AuthProvider sem sessao previa (401) -> anonymous, pra tela aparecer neutra antes do teste interagir.
    vi.mocked(fetchCurrentUser).mockRejectedValue(new ApiError(401, null));
  });

  it("envia email e senha pro backend ao entrar com credenciais validas", async () => {
    const user = userEvent.setup();
    vi.mocked(loginUser).mockResolvedValue({ id: "1", email: "joao@example.com", createdAt: "2026-01-01T00:00:00Z" });

    renderLoginPage();
    await waitFor(() => expect(fetchCurrentUser).toHaveBeenCalled());

    await user.type(screen.getByLabelText("E-mail"), "joao@example.com");
    await user.type(screen.getByLabelText("Senha"), "senhaSuperSegura123");
    await user.click(screen.getByRole("button", { name: "Entrar" }));

    await waitFor(() => {
      expect(loginUser).toHaveBeenCalledWith({ email: "joao@example.com", password: "senhaSuperSegura123" });
    });
  });

  it("mostra o detail do backend quando as credenciais sao invalidas", async () => {
    const user = userEvent.setup();
    vi.mocked(loginUser).mockRejectedValue(new ApiError(401, { detail: "E-mail ou senha inválidos" }));

    renderLoginPage();
    await waitFor(() => expect(fetchCurrentUser).toHaveBeenCalled());

    await user.type(screen.getByLabelText("E-mail"), "joao@example.com");
    await user.type(screen.getByLabelText("Senha"), "senhaErrada");
    await user.click(screen.getByRole("button", { name: "Entrar" }));

    expect(await screen.findByText("E-mail ou senha inválidos")).toBeInTheDocument();
  });

  it("valida o formato do e-mail antes de chamar o backend", async () => {
    const user = userEvent.setup();

    renderLoginPage();
    await waitFor(() => expect(fetchCurrentUser).toHaveBeenCalled());

    await user.type(screen.getByLabelText("E-mail"), "nao-e-um-email");
    await user.type(screen.getByLabelText("Senha"), "qualquer-coisa");
    await user.click(screen.getByRole("button", { name: "Entrar" }));

    expect(await screen.findByText("E-mail inválido")).toBeInTheDocument();
    expect(loginUser).not.toHaveBeenCalled();
  });
});
