/**
 * Cliente HTTP do fin-mec. Fino wrapper sobre fetch:
 *  - base fixa em /api (context-path do backend; em dev o Vite faz proxy);
 *  - credentials: "include" para o cookie de sessao (JSESSIONID);
 *  - injeta o header CSRF nas mutacoes lendo o cookie XSRF-TOKEN direto
 *    (ver secao CSRF abaixo — nao usar o campo "token" do corpo de /api/csrf);
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
// SecurityConfig usa CsrfConfigurer.spa(): quando a mutacao chega com o header
// X-XSRF-TOKEN preenchido, o backend resolve o valor em modo "plain" (compara
// direto com o token cru guardado no CsrfTokenRepository) — NAO faz o
// decode XOR/BREACH que so se aplica ao fallback via parametro de formulario.
// Ou seja, o header precisa do valor CRU do cookie XSRF-TOKEN, nao do campo
// "token" retornado no corpo JSON de GET /api/csrf (esse e o valor mascarado,
// pensado pra ir num campo _csrf de formulario HTML, nao no header). O cookie
// tem path "/" (CookieCsrfTokenRepository#setCookiePath, ver SecurityConfig no
// backend), entao o SPA servido em "/" consegue ler com document.cookie.
const CSRF_COOKIE_NAME = "XSRF-TOKEN";
let csrfHeaderName = "X-XSRF-TOKEN"; // default do Spring; bootstrapCsrf() confirma via /api/csrf

export function setCsrfHeaderName(headerName: string): void {
  csrfHeaderName = headerName;
}

function readCsrfCookie(): string | null {
  const match = document.cookie.match(new RegExp(`(?:^|;\\s*)${CSRF_COOKIE_NAME}=([^;]*)`));
  return match ? decodeURIComponent(match[1]) : null;
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
  if (!SAFE_METHODS.has(method)) {
    const token = readCsrfCookie();
    if (token) {
      headers.set(csrfHeaderName, token);
    }
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
