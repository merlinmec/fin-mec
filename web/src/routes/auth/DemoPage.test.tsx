import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { ApiError } from "@/api/client";
import { AuthProvider } from "@/auth/AuthProvider";
import { DemoPage } from "./DemoPage";
import { LoginPage } from "./LoginPage";

vi.mock("@/api/auth", () => ({
  fetchCurrentUser: vi.fn(),
  loginUser: vi.fn(),
  loginMfa: vi.fn(),
  registerUser: vi.fn(),
  logoutUser: vi.fn(),
}));
vi.mock("@/api/csrf", () => ({ bootstrapCsrf: vi.fn() }));
vi.mock("@/api/demo", async () => {
  const actual = await vi.importActual<typeof import("@/api/demo")>("@/api/demo");
  return { ...actual, getDemoStatus: vi.fn(), startDemoSession: vi.fn() };
});

import { fetchCurrentUser } from "@/api/auth";
import { bootstrapCsrf } from "@/api/csrf";
import { getDemoStatus, startDemoSession } from "@/api/demo";

const visitor = {
  id: "v1",
  email: "visitante-abc@visitante.fin-mec.invalid",
  createdAt: "2026-10-10T00:00:00Z",
  mfaEnabled: false,
};

function renderAt(path: string) {
  render(
    <QueryClientProvider
      client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
    >
      <MemoryRouter initialEntries={[path]}>
        <AuthProvider>
          <Routes>
            <Route path="/demo" element={<DemoPage />} />
            <Route path="/login" element={<LoginPage />} />
            <Route path="/" element={<h1>Dashboard</h1>} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("demonstração pública", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(bootstrapCsrf).mockResolvedValue(undefined);
    vi.mocked(fetchCurrentUser).mockRejectedValue(new ApiError(401, null));
    vi.mocked(startDemoSession).mockResolvedValue(visitor);
  });

  it("/demo entra como visitante sozinho e vai para o dashboard", async () => {
    vi.mocked(getDemoStatus).mockResolvedValue({ enabled: true });
    renderAt("/demo");

    expect(await screen.findByRole("heading", { name: "Dashboard" })).toBeInTheDocument();
    expect(startDemoSession).toHaveBeenCalledOnce();
  });

  it("/demo não cria outra conta para quem já é visitante", async () => {
    vi.mocked(getDemoStatus).mockResolvedValue({ enabled: true });
    vi.mocked(fetchCurrentUser).mockResolvedValue(visitor);
    renderAt("/demo");

    expect(await screen.findByRole("heading", { name: "Dashboard" })).toBeInTheDocument();
    expect(startDemoSession).not.toHaveBeenCalled();
  });

  it("mostra o erro e deixa tentar de novo quando a demo está cheia", async () => {
    const user = userEvent.setup();
    vi.mocked(getDemoStatus).mockResolvedValue({ enabled: true });
    vi.mocked(startDemoSession)
      .mockRejectedValueOnce(
        new ApiError(429, { detail: "A demonstração está com muitos visitantes agora." }),
      )
      .mockResolvedValueOnce(visitor);
    renderAt("/demo");

    expect(await screen.findByRole("alert")).toHaveTextContent("muitos visitantes");
    await user.click(screen.getByRole("button", { name: "Entrar como visitante" }));

    expect(await screen.findByRole("heading", { name: "Dashboard" })).toBeInTheDocument();
  });

  it("na demo, o login troca o formulário pelo cartão de visitante", async () => {
    vi.mocked(getDemoStatus).mockResolvedValue({ enabled: true });
    renderAt("/login");

    // O login renderiza duas vezes enquanto a sessão é conferida: consulta de novo a cada tentativa.
    await waitFor(() =>
      expect(screen.getByRole("button", { name: "Entrar como visitante" })).toBeInTheDocument(),
    );
    expect(screen.queryByLabelText("Senha")).not.toBeInTheDocument();
    expect(screen.queryByText("Criar conta")).not.toBeInTheDocument();
  });

  it("fora da demo, /demo explica e o login continua normal", async () => {
    vi.mocked(getDemoStatus).mockResolvedValue({ enabled: false });
    renderAt("/demo");

    expect(await screen.findByText("Demonstração indisponível")).toBeInTheDocument();
    await waitFor(() => expect(startDemoSession).not.toHaveBeenCalled());
  });
});
