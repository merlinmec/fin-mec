/** Cache do service worker com as respostas de API das telas de leitura (vite.config.ts). */
export const API_CACHE = "fin-mec-api";

/**
 * Apaga as respostas de API guardadas para uso offline. Chamado na troca de usuário (inclusive
 * logout): num celular compartilhado, o extrato de uma pessoa não pode sobrar para a próxima.
 */
export async function clearOfflineData(): Promise<void> {
  if (typeof caches === "undefined") return;
  try {
    await caches.delete(API_CACHE);
  } catch {
    // Sem Cache Storage (modo privado de alguns navegadores): não há o que apagar.
  }
}
