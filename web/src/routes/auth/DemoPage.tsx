import { useEffect, useRef, useState } from "react";
import { Loader2 } from "lucide-react";
import { Link, useNavigate } from "react-router-dom";
import { isVisitor } from "@/api/demo";
import { useAuth } from "@/auth/auth-context";
import { useDemoStatus } from "@/hooks/useDemoStatus";
import { Button } from "@/components/ui/button";
import { buttonVariants } from "@/components/ui/button-variants";
import { getErrorMessage } from "@/lib/errors";
import { cn } from "@/lib/utils";

/**
 * Cartão "Entrar como visitante" da tela de login na demonstração. Também é o conteúdo de /demo,
 * onde a entrada começa sozinha (o botão "Testar" do portfólio aponta para lá).
 */
export function VisitorEntry({ autoStart = false }: { autoStart?: boolean }) {
  const { startDemo, status, user } = useAuth();
  const navigate = useNavigate();
  const [starting, setStarting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const started = useRef(false);

  async function enter() {
    setError(null);
    setStarting(true);
    try {
      await startDemo();
      void navigate("/", { replace: true });
    } catch (err) {
      setError(getErrorMessage(err, "Não foi possível abrir a demonstração. Tente de novo."));
      setStarting(false);
    }
  }

  useEffect(() => {
    if (!autoStart || status === "loading" || started.current) return;
    started.current = true;
    // Quem já está numa conta de visitante (voltou pelo link) continua nela.
    if (status === "authenticated" && isVisitor(user)) {
      void navigate("/", { replace: true });
      return;
    }
    void enter();
    // eslint-disable-next-line react-hooks/exhaustive-deps -- dispara uma vez, quando a sessão é conhecida
  }, [autoStart, status]);

  return (
    <>
      <h1 className="text-lg font-semibold tracking-tight">Demonstração do fin-mec</h1>
      <p className="mt-2 text-sm text-muted-foreground">
        Entre numa conta temporária já com seis meses de dados fictícios: contas, cartão, orçamento,
        metas e relatórios. Ela é só sua e é apagada em 24 horas.
      </p>
      {error && (
        <p
          role="alert"
          className="mt-4 rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive"
        >
          {error}
        </p>
      )}
      <Button className="mt-5 w-full" onClick={() => void enter()} disabled={starting}>
        {starting ? (
          <>
            <Loader2 className="size-4 animate-spin" aria-hidden />
            Preparando seus dados…
          </>
        ) : (
          "Entrar como visitante"
        )}
      </Button>
      <p className="mt-4 text-center text-xs text-muted-foreground">
        Código-fonte:{" "}
        <a
          href="https://github.com/merlinmec/fin-mec"
          className="font-medium text-primary hover:underline"
          target="_blank"
          rel="noreferrer"
        >
          github.com/merlinmec/fin-mec
        </a>
      </p>
    </>
  );
}

/** /demo: entra como visitante sem passar pela tela de login. Fora da demonstração, manda ao login. */
export function DemoPage() {
  const demo = useDemoStatus();

  return (
    <div className="grid min-h-dvh place-items-center bg-muted/40 p-6">
      <div className="w-full max-w-sm">
        <div className="mb-6 flex items-center justify-center gap-2">
          <span className="flex size-8 items-center justify-center rounded-full bg-primary text-sm font-bold text-primary-foreground">
            f
          </span>
          <span className="text-lg font-semibold tracking-tight">fin-mec</span>
        </div>
        <div className="rounded-2xl border border-border/60 bg-card p-6 text-card-foreground shadow-sm">
          {demo.data?.enabled ? (
            <VisitorEntry autoStart />
          ) : demo.isPending ? (
            <p className="flex items-center gap-2 text-sm text-muted-foreground" role="status">
              <Loader2 className="size-4 animate-spin" aria-hidden /> Carregando…
            </p>
          ) : (
            <>
              <h1 className="text-lg font-semibold tracking-tight">Demonstração indisponível</h1>
              <p className="mt-2 text-sm text-muted-foreground">
                Este servidor não é a demonstração pública.
              </p>
              <Link to="/login" className={cn(buttonVariants(), "mt-5 w-full")}>
                Ir para o login
              </Link>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
