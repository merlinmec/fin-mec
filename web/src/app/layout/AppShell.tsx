import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { cn } from "@/lib/utils";
import { useAuth } from "@/auth/auth-context";
import { Button } from "@/components/ui/button";

/**
 * Casca autenticada: sidebar de navegacao + topbar. As secoes ainda nao tem
 * tela (chegam nas fases FE-2..FE-8); os itens ficam desabilitados ate la.
 */
const NAV_SECTIONS = [
  { label: "Dashboard", to: "/", ready: false },
  { label: "Contas", to: "/contas", ready: true },
  { label: "Categorias", to: "/categorias", ready: true },
  { label: "Lançamentos", to: "/lancamentos", ready: true },
  { label: "Orçamento", to: "/orcamento", ready: false },
  { label: "Contas a pagar", to: "/contas-a-pagar", ready: false },
  { label: "Cartões", to: "/cartoes", ready: false },
] as const;

export function AppShell() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  async function handleLogout() {
    await logout();
    void navigate("/login", { replace: true });
  }

  return (
    <div className="grid min-h-dvh grid-cols-[15rem_1fr]">
      <aside className="flex flex-col gap-1 border-r border-border bg-card px-3 py-4">
        <div className="px-2 pb-4 text-lg font-semibold tracking-tight">fin-mec</div>
        <nav className="flex flex-col gap-0.5">
          {NAV_SECTIONS.map((section) =>
            section.ready ? (
              <NavLink
                key={section.to}
                to={section.to}
                end={(section.to as string) === "/"}
                className={({ isActive }) =>
                  cn(
                    "rounded-md px-2 py-1.5 text-sm text-muted-foreground hover:bg-accent hover:text-accent-foreground",
                    isActive && "bg-accent font-medium text-accent-foreground",
                  )
                }
              >
                {section.label}
              </NavLink>
            ) : (
              <span
                key={section.to}
                aria-disabled
                className="cursor-default rounded-md px-2 py-1.5 text-sm text-muted-foreground/50"
              >
                {section.label}
              </span>
            ),
          )}
        </nav>
      </aside>

      <div className="flex flex-col">
        <header className="flex h-14 items-center justify-end gap-4 border-b border-border px-6 text-sm text-muted-foreground">
          <span>{user?.email}</span>
          <Button variant="outline" size="sm" onClick={() => void handleLogout()}>
            Sair
          </Button>
        </header>
        <main className="flex-1 p-6">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
