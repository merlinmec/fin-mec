import { useAuth } from "@/auth/auth-context";
import { Button } from "@/components/ui/button";

/**
 * Placeholder da FE-0. A tela de login/registro de verdade chega na FE-1;
 * aqui so evidenciamos que o boot da sessao rodou e o estado e "anonymous".
 */
export function LoginPlaceholder() {
  const { status, refresh } = useAuth();

  return (
    <div className="grid min-h-dvh place-items-center p-6">
      <div className="w-full max-w-sm rounded-xl border border-border bg-card p-6 text-card-foreground shadow-sm">
        <h1 className="text-lg font-semibold tracking-tight">fin-mec</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Fundação do frontend (FE-0). A autenticação chega na próxima fase.
        </p>
        <dl className="mt-4 space-y-1 text-sm">
          <div className="flex justify-between">
            <dt className="text-muted-foreground">Sessão</dt>
            <dd className="font-medium">{status}</dd>
          </div>
          <div className="flex justify-between">
            <dt className="text-muted-foreground">API</dt>
            <dd className="font-medium">/api</dd>
          </div>
        </dl>
        <Button className="mt-5 w-full" onClick={() => void refresh()}>
          Rechecar sessão
        </Button>
      </div>
    </div>
  );
}
