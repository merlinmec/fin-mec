/**
 * Destino de ?next= depois de entrar/criar conta. Só caminho interno ("/x", nunca "//evil.com"
 * nem "https://..."): evita open redirect.
 */
export function safeNext(next: string | null): string {
  return next && next.startsWith("/") && !next.startsWith("//") && !next.startsWith("/\\")
    ? next
    : "/";
}

/** Sufixo "?next=..." para repassar o destino entre login e cadastro. */
export function nextQuery(next: string | null): string {
  return next ? `?next=${encodeURIComponent(next)}` : "";
}
