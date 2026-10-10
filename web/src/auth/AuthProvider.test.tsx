import { beforeEach, describe, expect, it, vi } from "vitest";
import { act, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useContext } from "react";
import { ApiError, api } from "@/api/client";
import { AuthContext } from "./auth-context";
import { AuthProvider } from "./AuthProvider";

vi.mock("@/api/auth", () => ({
  fetchCurrentUser: vi.fn(),
  loginUser: vi.fn(),
  loginMfa: vi.fn(),
  registerUser: vi.fn(),
  logoutUser: vi.fn(),
}));
vi.mock("@/api/csrf", () => ({ bootstrapCsrf: vi.fn() }));
vi.mock("@/app/offline-cache", () => ({ clearOfflineData: vi.fn() }));

import { fetchCurrentUser, logoutUser } from "@/api/auth";
import { bootstrapCsrf } from "@/api/csrf";
import { clearOfflineData } from "@/app/offline-cache";

const joao = {
  id: "1",
  email: "joao@example.com",
  createdAt: "2026-01-01T00:00:00Z",
  mfaEnabled: false,
};
const maria = {
  id: "2",
  email: "maria@example.com",
  createdAt: "2026-01-01T00:00:00Z",
  mfaEnabled: false,
};

let auth: React.ContextType<typeof AuthContext>;

function Probe() {
  auth = useContext(AuthContext);
  return <span>{auth?.status}</span>;
}

function renderProvider(queryClient = new QueryClient()) {
  render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <Probe />
      </AuthProvider>
    </QueryClientProvider>,
  );
  return queryClient;
}

describe("AuthProvider", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(bootstrapCsrf).mockResolvedValue(undefined);
    vi.mocked(logoutUser).mockResolvedValue(undefined);
  });

  it("preserva o cache offline quando a mesma pessoa reabre o app", async () => {
    vi.mocked(fetchCurrentUser).mockResolvedValue(joao);

    renderProvider();

    expect(await screen.findByText("authenticated")).toBeInTheDocument();
    expect(clearOfflineData).not.toHaveBeenCalled();
  });

  it("apaga o cache ao abrir o app com a sessao expirada", async () => {
    vi.mocked(fetchCurrentUser).mockRejectedValue(new ApiError(401, null));

    renderProvider();

    expect(await screen.findByText("anonymous")).toBeInTheDocument();
    expect(clearOfflineData).toHaveBeenCalled();
  });

  it("apaga o cache e as queries no logout", async () => {
    vi.mocked(fetchCurrentUser).mockResolvedValue(joao);
    const queryClient = renderProvider();
    await screen.findByText("authenticated");
    queryClient.setQueryData(["accounts"], [{ id: "conta-do-joao" }]);

    await act(() => auth!.logout());

    expect(screen.getByText("anonymous")).toBeInTheDocument();
    expect(clearOfflineData).toHaveBeenCalled();
    expect(queryClient.getQueryData(["accounts"])).toBeUndefined();
  });

  it("apaga o cache quando a sessao passa a ser de outra pessoa", async () => {
    vi.mocked(fetchCurrentUser).mockResolvedValue(joao);
    renderProvider();
    await screen.findByText("authenticated");
    expect(clearOfflineData).not.toHaveBeenCalled();

    act(() => auth!.setCurrentUser(maria));

    await waitFor(() => expect(clearOfflineData).toHaveBeenCalled());
  });

  it("rebaixa para anonymous quando qualquer chamada recebe 401", async () => {
    vi.mocked(fetchCurrentUser).mockResolvedValue(joao);
    renderProvider();
    await screen.findByText("authenticated");

    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(null, { status: 401 })));
    await act(() => api.get("/dashboard").catch(() => undefined));
    vi.unstubAllGlobals();

    expect(screen.getByText("anonymous")).toBeInTheDocument();
  });
});
