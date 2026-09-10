import { NavLink, Outlet } from "react-router-dom";
import { cn } from "@/lib/utils";
import { useAuth } from "@/auth/auth-context";

/**
 * Casca autenticada: sidebar de navegacao + topbar. As secoes ainda nao tem
 * tela (chegam nas fases FE-2..FE-8); os itens ficam desabilitados ate la.
 */
const NAV_SECTIONS = [
  { label: "Dashboard", to: "/", ready: false },
  { label: "Contas", to: "/contas", ready: false },
  { label: "Lançamentos", to: "/lancamentos", ready: false },
  { label: "Orçamento", to: "/orcamento", ready: false },
  { label: "Contas a pagar", to: "/contas-a-pagar", ready: false },
  { label: "Cartões", to: "/cartoes", ready: false },
] as const;

export function AppShell() {
  const { user } = useAuth();

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
                end={section.to === "/"}
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
        <header className="flex h-14 items-center justify-end border-b border-border px-6 text-sm text-muted-foreground">
          {user?.email}
        </header>
        <main className="flex-1 p-6">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
