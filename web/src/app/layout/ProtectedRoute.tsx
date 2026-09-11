import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "@/auth/auth-context";

/**
 * Enquanto o boot da sessao roda, mostra um placeholder. Anonimo -> /login
 * preservando a rota de origem em ?next=. Autenticado -> renderiza a casca.
 */
export function ProtectedRoute() {
  const { status } = useAuth();
  const location = useLocation();

  if (status === "loading") {
    return (
      <div className="grid min-h-dvh place-items-center text-sm text-muted-foreground">
        Carregando…
      </div>
    );
  }

  if (status === "anonymous") {
    const next = encodeURIComponent(location.pathname + location.search);
    return <Navigate to={`/login?next=${next}`} replace />;
  }

  return <Outlet />;
}
