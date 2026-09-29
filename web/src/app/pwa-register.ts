import { toast } from "sonner";
import { registerSW } from "virtual:pwa-register";

/**
 * Registra o service worker (Fase 20). Só importado por main.tsx: o módulo virtual do
 * vite-plugin-pwa não existe nos testes. Versão nova só entra quando o usuário aceita —
 * recarregar sozinho poderia jogar fora um lançamento sendo digitado.
 */
export function startPwa(): void {
  if (!("serviceWorker" in navigator) || import.meta.env.DEV) return;
  const update = registerSW({
    onNeedRefresh() {
      toast("Nova versão do fin-mec disponível", {
        duration: Infinity,
        action: { label: "Atualizar", onClick: () => void update(true) },
      });
    },
  });
}
