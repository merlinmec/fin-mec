import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, api, apiBlob, apiFetch, setUnauthorizedHandler } from "./client";

const fetchMock = vi.fn<typeof fetch>();

function jsonResponse(body: unknown, init: ResponseInit = {}): Response {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { "Content-Type": "application/json" },
    ...init,
  });
}

function sentHeaders(): Headers {
  return fetchMock.mock.calls[0][1]!.headers as Headers;
}

describe("apiFetch", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", fetchMock);
    document.cookie = "XSRF-TOKEN=token%2Fcru; path=/";
  });

  afterEach(() => {
    fetchMock.mockReset();
    vi.unstubAllGlobals();
    setUnauthorizedHandler(null);
    document.cookie = "XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/";
  });

  it("manda o valor cru do cookie XSRF-TOKEN no header das mutacoes", async () => {
    // Regressao da FE-1: o header precisa do cookie decodificado, nao do token mascarado de /api/csrf.
    fetchMock.mockResolvedValue(jsonResponse({ id: "1" }));

    await api.post("/accounts", { name: "Conta" });

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("/api/accounts");
    expect(init!.credentials).toBe("include");
    expect(sentHeaders().get("X-XSRF-TOKEN")).toBe("token/cru");
    expect(sentHeaders().get("Content-Type")).toBe("application/json");
    expect(init!.body).toBe(JSON.stringify({ name: "Conta" }));
  });

  it("nao manda o header CSRF em GET", async () => {
    fetchMock.mockResolvedValue(jsonResponse([]));

    await api.get("/accounts");

    expect(sentHeaders().has("X-XSRF-TOKEN")).toBe(false);
  });

  it("deixa o navegador definir o Content-Type de upload multipart", async () => {
    fetchMock.mockResolvedValue(jsonResponse({}));
    const form = new FormData();
    form.append("file", new Blob(["x"]), "extrato.ofx");

    await apiFetch("/imports/preview", { method: "POST", body: form });

    expect(sentHeaders().has("Content-Type")).toBe(false);
    expect(sentHeaders().get("X-XSRF-TOKEN")).toBe("token/cru");
  });

  it("chama o handler global e lanca ApiError em 401", async () => {
    const onUnauthorized = vi.fn();
    setUnauthorizedHandler(onUnauthorized);
    fetchMock.mockResolvedValue(jsonResponse({ title: "Unauthorized" }, { status: 401 }));

    await expect(api.get("/auth/me")).rejects.toMatchObject({ status: 401 });
    expect(onUnauthorized).toHaveBeenCalledOnce();
  });

  it("converte o ProblemDetail do backend em ApiError com a mensagem do detail", async () => {
    const onUnauthorized = vi.fn();
    setUnauthorizedHandler(onUnauthorized);
    fetchMock.mockResolvedValue(
      jsonResponse(
        { title: "Bad Request", detail: "Valor inválido", errors: ["amount: deve ser positivo"] },
        { status: 400 },
      ),
    );

    const error = await api.post("/transactions", {}).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).message).toBe("Valor inválido");
    expect((error as ApiError).problem?.errors).toEqual(["amount: deve ser positivo"]);
    expect(onUnauthorized).not.toHaveBeenCalled();
  });

  it("usa o status HTTP como mensagem quando o erro nao tem corpo JSON", async () => {
    fetchMock.mockResolvedValue(new Response("<html>bad gateway</html>", { status: 502 }));

    await expect(api.get("/dashboard")).rejects.toThrow("HTTP 502");
  });

  it("devolve undefined em 204", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));

    await expect(api.del("/accounts/1")).resolves.toBeUndefined();
  });
});

describe("apiBlob", () => {
  beforeEach(() => vi.stubGlobal("fetch", fetchMock));
  afterEach(() => {
    fetchMock.mockReset();
    vi.unstubAllGlobals();
  });

  it("prefere o filename* (UTF-8) do Content-Disposition", async () => {
    fetchMock.mockResolvedValue(
      new Response("pdf", {
        headers: {
          "Content-Disposition":
            "attachment; filename=\"recibo.pdf\"; filename*=UTF-8''recibo%20%C3%A1gua.pdf",
        },
      }),
    );

    const { fileName } = await apiBlob("/attachments/1/content");

    expect(fileName).toBe("recibo água.pdf");
  });

  it("cai para o filename simples e devolve null sem Content-Disposition", async () => {
    fetchMock
      .mockResolvedValueOnce(
        new Response("x", {
          headers: { "Content-Disposition": 'attachment; filename="dados.json"' },
        }),
      )
      .mockResolvedValueOnce(new Response("x"));

    expect((await apiBlob("/a")).fileName).toBe("dados.json");
    expect((await apiBlob("/b")).fileName).toBeNull();
  });
});
