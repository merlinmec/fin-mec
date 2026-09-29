import { useState } from "react";
import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { Menu, X } from "lucide-react";
import { cn } from "@/lib/utils";
import { useAuth } from "@/auth/auth-context";
import { Button } from "@/components/ui/button";
import { NotificationBell } from "@/components/NotificationBell";

/**
 * Casca autenticada: topbar verde full-width com nav horizontal (padrão
 * Organizze — produto de referência do projeto, ver PRODUCT.md/DESIGN.md),
 * substituindo a sidebar fixa da FE-0..FE-9. Abaixo de lg a nav horizontal
 * não cabe: vira um menu suspenso (dropdown abaixo do topbar) aberto pelo
 * botão de hambúrguer, em vez de uma gaveta lateral.
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

const navLinkClass = ({ isActive }: { isActive: boolean }) =>
  cn(
    "rounded-full px-3 py-1.5 text-sm font-medium whitespace-nowrap text-primary-foreground/80 transition-colors hover:bg-white/10 hover:text-primary-foreground",
    isActive && "bg-white/15 text-primary-foreground",
  );

const mobileNavLinkClass = ({ isActive }: { isActive: boolean }) =>
  cn(
    "block rounded-md px-3 py-2 text-sm font-medium text-primary-foreground/85 hover:bg-white/10",
    isActive && "bg-white/15 text-primary-foreground",
  );

export function AppShell() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [menuOpen, setMenuOpen] = useState(false);

  async function handleLogout() {
    await logout();
    void navigate("/login", { replace: true });
  }

  return (
    <div className="min-h-dvh bg-background">
      <header className="sticky top-0 z-40 bg-primary text-primary-foreground shadow-sm">
        <div className="flex h-14 items-center gap-1 px-4 sm:px-6">
          <span className="mr-4 shrink-0 text-lg font-semibold tracking-tight">fin-mec</span>

          <nav className="hidden items-center gap-1 overflow-x-auto lg:flex">
            {NAV_SECTIONS.map((section) =>
              section.ready ? (
                <NavLink key={section.to} to={section.to} end={section.to === "/"} className={navLinkClass}>
                  {section.label}
                </NavLink>
              ) : (
                <span
                  key={section.to}
                  aria-disabled
                  className="cursor-default rounded-full px-3 py-1.5 text-sm text-primary-foreground/40"
                >
                  {section.label}
                </span>
              ),
            )}
          </nav>

          <div className="flex-1" />

          <NotificationBell triggerClassName="text-primary-foreground hover:bg-white/15 hover:text-primary-foreground" />
          <span className="hidden truncate pl-1 text-sm text-primary-foreground/90 sm:inline">{user?.email}</span>
          <Button
            variant="outline"
            size="sm"
            className="hidden border-white/30 bg-transparent text-primary-foreground hover:bg-white/10 hover:text-primary-foreground lg:ml-2 lg:inline-flex"
            onClick={() => void handleLogout()}
          >
            Sair
          </Button>

          <Button
            variant="ghost"
            size="icon"
            className="ml-1 text-primary-foreground hover:bg-white/15 hover:text-primary-foreground lg:hidden"
            onClick={() => setMenuOpen((v) => !v)}
            aria-label={menuOpen ? "Fechar menu" : "Abrir menu"}
            aria-expanded={menuOpen}
          >
            {menuOpen ? <X className="size-5" /> : <Menu className="size-5" />}
          </Button>
        </div>

        {menuOpen && (
          <nav className="space-y-0.5 border-t border-white/15 px-2 pt-2 pb-3 lg:hidden">
            {NAV_SECTIONS.map((section) =>
              section.ready ? (
                <NavLink
                  key={section.to}
                  to={section.to}
                  end={section.to === "/"}
                  onClick={() => setMenuOpen(false)}
                  className={mobileNavLinkClass}
                >
                  {section.label}
                </NavLink>
              ) : (
                <span
                  key={section.to}
                  aria-disabled
                  className="block cursor-default rounded-md px-3 py-2 text-sm text-primary-foreground/40"
                >
                  {section.label}
                </span>
              ),
            )}
            <div className="my-1 border-t border-white/15" />
            <div className="truncate px-3 py-1 text-xs text-primary-foreground/60">{user?.email}</div>
            <button
              type="button"
              onClick={() => void handleLogout()}
              className="block w-full rounded-md px-3 py-2 text-left text-sm font-medium text-primary-foreground/85 hover:bg-white/10"
            >
              Sair
            </button>
          </nav>
        )}
      </header>

      <main className="mx-auto max-w-6xl overflow-x-hidden p-4 sm:p-6">
        <Outlet />
      </main>
    </div>
  );
}
