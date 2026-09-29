import { useEffect, useState } from "react";
import { WifiOff } from "lucide-react";

/** Faixa discreta quando a conexão cai: o que aparece é o último dado salvo (Fase 20). */
export function OfflineBanner() {
  const [offline, setOffline] = useState(() => !navigator.onLine);
  useEffect(() => {
    const on = () => setOffline(false);
    const off = () => setOffline(true);
    window.addEventListener("online", on);
    window.addEventListener("offline", off);
    return () => {
      window.removeEventListener("online", on);
      window.removeEventListener("offline", off);
    };
  }, []);
  if (!offline) return null;
  return (
    <div
      role="status"
      className="flex items-center justify-center gap-2 bg-warning/15 px-4 py-1.5 text-center text-xs font-medium text-warning"
    >
      <WifiOff className="size-3.5 shrink-0" />
      Sem conexão — mostrando os últimos dados salvos. Lançar e editar volta quando a internet
      voltar.
    </div>
  );
}
