import { api } from "./client";

/** Espelha com.mecfin.identity.api.UserResponse. */
export interface CurrentUser {
  id: string;
  email: string;
  createdAt: string;
}

/** Espelha com.mecfin.identity.api.RegisterRequest (senha: 10-72 caracteres). */
export interface RegisterPayload {
  email: string;
  password: string;
}

/** Espelha com.mecfin.identity.api.LoginRequest. */
export interface LoginPayload {
  email: string;
  password: string;
}

/** GET /api/auth/me — 200 com o usuario da sessao, ou 401 se nao autenticado. */
export function fetchCurrentUser(): Promise<CurrentUser> {
  return api.get<CurrentUser>("/auth/me");
}

/**
 * POST /api/auth/register — cria o usuario e ja autentica (o backend abre a
 * sessao na mesma chamada). Rate limit por IP: 429 se exceder o limite.
 */
export function registerUser(payload: RegisterPayload): Promise<CurrentUser> {
  return api.post<CurrentUser>("/auth/register", payload);
}

/**
 * POST /api/auth/login — autentica por sessao (cookie). Rate limit por
 * IP+e-mail: 429 se exceder o limite. 401 se credenciais invalidas.
 */
export function loginUser(payload: LoginPayload): Promise<CurrentUser> {
  return api.post<CurrentUser>("/auth/login", payload);
}

/** POST /api/auth/logout — invalida a sessao no backend (204 No Content). */
export function logoutUser(): Promise<void> {
  return api.post<void>("/auth/logout");
}
