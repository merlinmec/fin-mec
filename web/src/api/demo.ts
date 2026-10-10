import { api } from "./client";
import type { CurrentUser } from "./auth";

/** Domínio reservado das contas de visitante (espelha VisitorAccountService.EMAIL_DOMAIN). */
export const VISITOR_EMAIL_DOMAIN = "@visitante.fin-mec.invalid";

export function isVisitor(user: Pick<CurrentUser, "email"> | null | undefined): boolean {
  return !!user && user.email.toLowerCase().endsWith(VISITOR_EMAIL_DOMAIN);
}

/** GET /demo/status — este deploy é a demonstração pública? */
export function getDemoStatus(): Promise<{ enabled: boolean }> {
  return api.get<{ enabled: boolean }>("/demo/status");
}

/** POST /demo/session — conta temporária de visitante, já com dados fictícios, e sessão aberta. */
export function startDemoSession(): Promise<CurrentUser> {
  return api.post<CurrentUser>("/demo/session");
}
