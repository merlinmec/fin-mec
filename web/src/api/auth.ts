import { api } from "./client";

/** Espelha com.mecfin.identity.api.UserResponse. */
export interface CurrentUser {
  id: string;
  email: string;
  createdAt: string;
}

/** GET /api/auth/me — 200 com o usuario da sessao, ou 401 se nao autenticado. */
export function fetchCurrentUser(): Promise<CurrentUser> {
  return api.get<CurrentUser>("/auth/me");
}
