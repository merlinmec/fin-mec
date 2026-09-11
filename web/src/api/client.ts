/**
 * Cliente HTTP do fin-mec. Fino wrapper sobre fetch:
 *  - base fixa em /api (context-path do backend; em dev o Vite faz proxy);
 *  - credentials: "include" para o cookie de sessao (JSESSIONID);
 *  - injeta o header CSRF nas mutacoes a partir do token obtido em /api/csrf;
 *  - 401 dispara o handler global (limpar auth + redirecionar para /login);
 *  - erros viram ApiError com o corpo RFC 7807 (application/problem+json).
 *
 * Sem geracao de codigo: os tipos de DTO sao escritos a mao ao lado de cada
 * modulo de recurso (api/accounts.ts, api/transactions.ts, ...). O Swagger em
 * /api/swagger-ui.html e a referencia de contrato.
 */

const BASE = "/api";
const SAFE_METHODS = new Set(["GET", "HEAD", "OPTIONS", "TRACE"]);

/** Corpo de erro no formato ProblemDetail (RFC 7807) do backend. */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  /** Presente em erros de validacao: lista "campo: mensagem". */
  errors?: string[];
}

export class ApiError extends Error {
  readonly status: number;
  readonly problem: ProblemDetail | null;

  constructor(status: number, problem: ProblemDetail | null) {
    super(problem?.detail ?? problem?.title ?? `HTTP ${status}`);
    this.name = "ApiError";
    this.status = status;
    this.problem = problem;
  }
}

// --- CSRF -------------------------------------------------------------------
// O SPA e servido em "/" mas o cookie XSRF-TOKEN tem path "/api", entao
// document.cookie nao o enxerga. Por isso o token vem do corpo de GET /api/csrf
// (JSON { token, headerName, parameterName }) e fica em memoria aqui.

interface CsrfState {
  headerName: string;
  token: string;
}

let csrf: CsrfState | null = null;

export function setCsrfToken(next: CsrfState | null): void {
  csrf = next;
}

// --- 401 handler ----------------------------------------------------------

type UnauthorizedHandler = () => void;

let onUnauthorized: UnauthorizedHandler | null = null;

export function setUnauthorizedHandler(handler: UnauthorizedHandler | null): void {
  onUnauthorized = handler;
}

// --- core ---------------------------------------------------------------

async function readProblem(res: Response): Promise<ProblemDetail | null> {
  try {
    return (await res.json()) as ProblemDetail;
  } catch {
    return null;
  }
}

export async function apiFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method ?? "GET").toUpperCase();
  const headers = new Headers(init.headers);

  if (init.body !== undefined && init.body !== null && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  if (!SAFE_METHODS.has(method) && csrf) {
    headers.set(csrf.headerName, csrf.token);
  }

  const res = await fetch(BASE + path, { ...init, method, headers, credentials: "include" });

  if (res.status === 401) {
    onUnauthorized?.();
    throw new ApiError(401, await readProblem(res));
  }
  if (!res.ok) {
    throw new ApiError(res.status, await readProblem(res));
  }
  if (res.status === 204 || res.headers.get("Content-Length") === "0") {
    return undefined as T;
  }

  const contentType = res.headers.get("Content-Type") ?? "";
  if (contentType.includes("json")) {
    return (await res.json()) as T;
  }
  return (await res.text()) as T;
}

function jsonBody(body: unknown): string | undefined {
  return body === undefined ? undefined : JSON.stringify(body);
}

export const api = {
  get: <T>(path: string): Promise<T> => apiFetch<T>(path),
  post: <T>(path: string, body?: unknown): Promise<T> =>
    apiFetch<T>(path, { method: "POST", body: jsonBody(body) }),
  put: <T>(path: string, body?: unknown): Promise<T> =>
    apiFetch<T>(path, { method: "PUT", body: jsonBody(body) }),
  patch: <T>(path: string, body?: unknown): Promise<T> =>
    apiFetch<T>(path, { method: "PATCH", body: jsonBody(body) }),
  del: <T>(path: string): Promise<T> => apiFetch<T>(path, { method: "DELETE" }),
};
