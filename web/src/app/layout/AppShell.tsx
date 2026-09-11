import { useState } from "react";
import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { Menu, X } from "lucide-react";
import { cn } from "@/lib/utils";
import { useAuth } from "@/auth/auth-context";
import { Button } from "@/components/ui/button";
import { NotificationBell } from "@/components/NotificationBell";

/**
 * Casca autenticada: sidebar de navegacao + topbar. Abaixo de lg a sidebar
 * vira uma gaveta (fixed, fora da tela por padrao) aberta pelo botao de
 * menu no topbar — o grid de 2 colunas so existe a partir de lg.
 */
const NAV_SECTIONS = [
  { label: "Dashboard", to: "/", ready: true },
  { label: "Contas", to: "/contas", ready: true },
  { label: "Categorias", to: "/categorias", ready: true },
  { label: "Lançamentos", to: "/lancamentos", ready: true },
  { label: "Orçamento", to: "/orcamento", ready: true },
  { label: "Contas a pagar", to: "/contas-a-pagar", ready: true },
  { label: "Cartões", to: "/cartoes", ready: true },
] as const;

export function AppShell() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [sidebarOpen, setSidebarOpen] = useState(false);

  async function handleLogout() {
    await logout();
    void navigate("/login", { replace: true });
  }

  return (
    <div className="min-h-dvh lg:grid lg:grid-cols-[15rem_1fr]">
      {sidebarOpen && (
        <button
          type="button"
          aria-label="Fechar menu"
          className="fixed inset-0 z-30 bg-black/50 lg:hidden"
          onClick={() => setSidebarOpen(false)}
        />
      )}

      <aside
        className={cn(
          "fixed inset-y-0 left-0 z-40 flex w-60 flex-col gap-1 border-r border-border bg-card px-3 py-4 transition-transform duration-200",
          "lg:static lg:translate-x-0",
          sidebarOpen ? "translate-x-0" : "-translate-x-full",
        )}
      >
        <div className="flex items-center justify-between px-2 pb-4">
          <span className="text-lg font-semibold tracking-tight">fin-mec</span>
          <Button
            variant="ghost"
            size="icon"
            className="lg:hidden"
            onClick={() => setSidebarOpen(false)}
            aria-label="Fechar menu"
          >
            <X className="size-4" />
          </Button>
        </div>
        <nav className="flex flex-col gap-0.5">
          {NAV_SECTIONS.map((section) =>
            section.ready ? (
              <NavLink
                key={section.to}
                to={section.to}
                end={(section.to as string) === "/"}
                onClick={() => setSidebarOpen(false)}
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

      <div className="flex min-w-0 flex-col">
        <header className="flex h-14 items-center gap-3 border-b border-border px-4 text-sm text-muted-foreground sm:px-6">
          <Button
            variant="ghost"
            size="icon"
            className="lg:hidden"
            onClick={() => setSidebarOpen(true)}
            aria-label="Abrir menu"
          >
            <Menu className="size-4" />
          </Button>
          <div className="flex-1" />
          <NotificationBell />
          <span className="hidden truncate sm:inline">{user?.email}</span>
          <Button variant="outline" size="sm" onClick={() => void handleLogout()}>
            Sair
          </Button>
        </header>
        <main className="flex-1 overflow-x-hidden p-4 sm:p-6">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
