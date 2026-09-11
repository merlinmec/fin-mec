import { createContext, useContext } from "react";
import type { CurrentUser, LoginPayload, RegisterPayload } from "@/api/auth";

export type AuthStatus = "loading" | "authenticated" | "anonymous";

export interface AuthContextValue {
  status: AuthStatus;
  user: CurrentUser | null;
  /** Re-checa a sessao no backend (usado apos login/logout nas fases seguintes). */
  refresh: () => Promise<void>;
  /** POST /auth/login. Lanca ApiError (401 credenciais invalidas, 429 rate limit) em caso de falha. */
  login: (payload: LoginPayload) => Promise<void>;
  /** POST /auth/register. Lanca ApiError (409 e-mail duplicado, 429 rate limit) em caso de falha. */
  register: (payload: RegisterPayload) => Promise<void>;
  /** POST /auth/logout. */
  logout: () => Promise<void>;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (ctx === null) {
    throw new Error("useAuth deve ser usado dentro de <AuthProvider>");
  }
  return ctx;
}
