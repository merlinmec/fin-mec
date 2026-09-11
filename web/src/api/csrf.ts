import { api, setCsrfToken } from "./client";

interface CsrfResponse {
  token: string;
  headerName: string;
  parameterName: string;
}

/**
 * Forca a emissao do token CSRF e o guarda em memoria no cliente HTTP.
 * Deve ser chamado no boot da aplicacao e novamente apos login/logout, pois o
 * Spring Security renova o token nesses momentos (CsrfAuthenticationStrategy).
 */
export async function bootstrapCsrf(): Promise<void> {
  const res = await api.get<CsrfResponse>("/csrf");
  setCsrfToken({ headerName: res.headerName, token: res.token });
}
