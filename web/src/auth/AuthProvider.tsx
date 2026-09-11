import { useCallback, useEffect, useState } from "react";
import {
  fetchCurrentUser,
  loginUser,
  logoutUser,
  registerUser,
  type CurrentUser,
  type LoginPayload,
  type RegisterPayload,
} from "@/api/auth";
import { bootstrapCsrf } from "@/api/csrf";
import { ApiError, setUnauthorizedHandler } from "@/api/client";
import { AuthContext, type AuthStatus } from "./auth-context";

/**
 * Faz o boot da sessao: semeia o token CSRF e consulta GET /api/auth/me.
 * 200 -> authenticated; 401 -> anonymous. Qualquer 401 posterior em chamadas
 * autenticadas tambem rebaixa o estado para anonymous (handler global).
 */
export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>("loading");
  const [user, setUser] = useState<CurrentUser | null>(null);

  const refresh = useCallback(async () => {
    try {
      await bootstrapCsrf();
      const me = await fetchCurrentUser();
      setUser(me);
      setStatus("authenticated");
    } catch (err) {
      // 401 = sessao ausente (esperado). Qualquer outra falha (backend fora,
      // rede) tambem cai para anonymous — a tela de login e o fallback seguro;
      // a FE-1 refina o tratamento de erro de infraestrutura.
      setUser(null);
      setStatus("anonymous");
      if (!(err instanceof ApiError && err.status === 401)) {
        console.error("Falha no boot da sessão", err);
      }
    }
  }, []);

  useEffect(() => {
    setUnauthorizedHandler(() => {
      setUser(null);
      setStatus("anonymous");
    });
    return () => setUnauthorizedHandler(null);
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  // O Spring renova o token CSRF a cada mudança de autenticação
  // (CsrfAuthenticationStrategy), então login/registro/logout precisam
  // re-semear o cliente depois da chamada — não só no boot.

  const login = useCallback(async (payload: LoginPayload) => {
    const me = await loginUser(payload);
    await bootstrapCsrf();
    setUser(me);
    setStatus("authenticated");
  }, []);

  const register = useCallback(async (payload: RegisterPayload) => {
    const me = await registerUser(payload);
    await bootstrapCsrf();
    setUser(me);
    setStatus("authenticated");
  }, []);

  const logout = useCallback(async () => {
    try {
      await logoutUser();
    } finally {
      await bootstrapCsrf();
      setUser(null);
      setStatus("anonymous");
    }
  }, []);

  return (
    <AuthContext.Provider value={{ status, user, refresh, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  );
}
