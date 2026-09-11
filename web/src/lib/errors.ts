import { ApiError } from "@/api/client";

/**
 * Extrai uma mensagem exibivel de um erro de mutacao. O backend (GlobalExceptionHandler)
 * ja escreve `detail` em portugues e pronto pra tela para os casos de negocio (401
 * credenciais invalidas, 409 e-mail duplicado, 429 rate limit, ...); so cai no fallback
 * generico para erros de rede/infra ou 5xx sem detail util.
 */
export function getErrorMessage(err: unknown, fallback = "Não foi possível completar a ação. Tente novamente."): string {
  if (err instanceof ApiError) {
    return err.problem?.detail ?? err.problem?.title ?? fallback;
  }
  return fallback;
}
