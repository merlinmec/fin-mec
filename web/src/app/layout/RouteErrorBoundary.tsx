import { Link, useNavigate, useRouteError } from "react-router-dom";
import { AlertTriangle } from "lucide-react";
import { Button } from "@/components/ui/button";
import { buttonVariants } from "@/components/ui/button-variants";

/**
 * errorElement por rota (React Router) — um erro de render numa tela nao
 * derruba o app inteiro nem some com a sidebar/topbar, so essa area some
 * pela mensagem. Usado em cada rota-folha do AppShell (ver router.tsx).
 */
export function RouteErrorBoundary() {
  const error = useRouteError();
  const navigate = useNavigate();

  if (import.meta.env.DEV) {
    console.error(error);
  }

  return (
    <div className="flex min-h-[50vh] flex-col items-center justify-center gap-3 p-6 text-center">
      <AlertTriangle className="size-8 text-destructive" />
      <h1 className="text-lg font-semibold tracking-tight">Algo deu errado nessa tela</h1>
      <p className="max-w-sm text-sm text-muted-foreground">
        Tente recarregar. Se o problema continuar, volte para o início.
      </p>
      <div className="flex gap-2">
        <Button variant="outline" onClick={() => navigate(0)}>
          Recarregar
        </Button>
        <Link to="/" className={buttonVariants({ variant: "default" })}>
          Ir para o início
        </Link>
      </div>
    </div>
  );
}
