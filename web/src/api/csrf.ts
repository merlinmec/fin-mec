import { api, setCsrfHeaderName } from "./client";

interface CsrfResponse {
  token: string;
  headerName: string;
  parameterName: string;
}

/**
 * Forca a resolucao do CsrfToken adiado no backend (deferred loading), o que
 * escreve o cookie XSRF-TOKEN na resposta. O client.ts le o valor do cookie
 * diretamente a cada mutacao; esta chamada so garante que o cookie exista e
 * guarda o headerName retornado (hoje sempre "X-XSRF-TOKEN", mas evita
 * hardcode). Deve ser chamada no boot e de novo apos login/logout, ja que o
 * Spring limpa e renova o token nesses momentos (CsrfAuthenticationStrategy).
 */
export async function bootstrapCsrf(): Promise<void> {
  const res = await api.get<CsrfResponse>("/csrf");
  setCsrfHeaderName(res.headerName);
}
