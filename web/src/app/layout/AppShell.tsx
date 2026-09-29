import { useEffect, useState } from "react";
import { NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";
import * as DialogPrimitive from "@radix-ui/react-dialog";
import {
  BarChart3,
  CalendarClock,
  ChevronDown,
  CreditCard,
  Eye,
  EyeOff,
  FileUp,
  Landmark,
  LayoutDashboard,
  ListOrdered,
  LogOut,
  Menu,
  Monitor,
  Moon,
  PiggyBank,
  Plus,
  Search,
  Settings,
  Shapes,
  ShieldCheck,
  Sun,
  Target,
  Wallet,
  type LucideIcon,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { useAuth } from "@/auth/auth-context";
import { QuickAddProvider, useQuickAdd } from "@/app/quick-add";
import { Button } from "@/components/ui/button";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { NotificationBell } from "@/components/NotificationBell";
import { CommandPalette } from "@/components/CommandPalette";
import { useBalanceVisibility } from "@/hooks/useBalanceVisibility";
import { useTheme, type ThemePreference } from "@/lib/theme";

/**
 * Casca autenticada (FE-10). Mantém a topologia "tudo no topo" do FE-9.1
 * (topbar verde, padrão Organizze), agora com: navegação principal + menu
 * "Mais", busca/paleta de comandos (Ctrl/⌘+K), "Novo lançamento" sempre à
 * mão e menu do usuário (tema, ocultar saldos, segurança). No mobile a nav
 * vira uma barra inferior com o botão de lançamento no centro — o polegar
 * alcança, e é assim que apps de finanças de uso diário funcionam.
 */
interface NavItem {
  label: string;
  to: string;
  icon: LucideIcon;
}

const PRIMARY_NAV: NavItem[] = [
  { label: "Dashboard", to: "/", icon: LayoutDashboard },
  { label: "Lançamentos", to: "/lancamentos", icon: ListOrdered },
  { label: "Relatórios", to: "/relatorios", icon: BarChart3 },
  { label: "Contas", to: "/contas", icon: Wallet },
  { label: "Cartões", to: "/cartoes", icon: CreditCard },
  { label: "Metas", to: "/metas", icon: Target },
];

const MORE_NAV: NavItem[] = [
  { label: "Bancos conectados", to: "/bancos", icon: Landmark },
  { label: "Importar extrato", to: "/importar", icon: FileUp },
  { label: "Contas a pagar", to: "/contas-a-pagar", icon: CalendarClock },
  { label: "Orçamento", to: "/orcamento", icon: PiggyBank },
  { label: "Categorias", to: "/categorias", icon: Shapes },
  { label: "Configurações", to: "/configuracoes", icon: Settings },
];

const ALL_NAV = [...PRIMARY_NAV, ...MORE_NAV];

export function AppShell() {
  return (
    <QuickAddProvider>
      <ShellLayout />
    </QuickAddProvider>
  );
}

function ShellLayout() {
  const [paletteOpen, setPaletteOpen] = useState(false);
  const [moreOpen, setMoreOpen] = useState(false);
  const balances = useBalanceVisibility();
  const { openEntry } = useQuickAdd();
  const location = useLocation();

  useEffect(() => {
    function onKeyDown(e: KeyboardEvent) {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        setPaletteOpen((v) => !v);
      }
    }
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, []);

  useEffect(() => setMoreOpen(false), [location.pathname]);

  const moreActive = MORE_NAV.some((item) => location.pathname.startsWith(item.to));

  return (
    <div className="min-h-dvh bg-background">
      <header className="sticky top-0 z-40 bg-topbar text-topbar-foreground shadow-[0_1px_0_oklch(0_0_0/0.08),0_4px_18px_-10px_oklch(0.3_0.1_148/0.6)]">
        <div className="mx-auto flex h-14 max-w-7xl items-center gap-1 px-4 sm:px-6">
          <NavLink
            to="/"
            className="mr-3 flex shrink-0 items-center gap-2"
            aria-label="fin-mec, ir para o dashboard"
          >
            <BrandMark />
            <span className="text-[1.05rem] font-bold tracking-tight">fin-mec</span>
          </NavLink>

          <nav className="hidden items-center gap-0.5 lg:flex" aria-label="Principal">
            {PRIMARY_NAV.map((item) => (
              <NavLink key={item.to} to={item.to} end={item.to === "/"} className={topLinkClass}>
                {item.label}
              </NavLink>
            ))}
            <Popover>
              <PopoverTrigger asChild>
                <button type="button" className={topLinkClass({ isActive: moreActive })}>
                  Mais <ChevronDown className="ml-0.5 inline size-3.5" />
                </button>
              </PopoverTrigger>
              <PopoverContent align="start" className="w-56 p-1.5">
                {MORE_NAV.map((item) => (
                  <MenuLink key={item.to} item={item} />
                ))}
              </PopoverContent>
            </Popover>
          </nav>

          <div className="flex-1" />

          <button
            type="button"
            onClick={() => setPaletteOpen(true)}
            className="hidden h-8 items-center gap-2 rounded-full bg-white/12 pr-2 pl-3 text-sm text-topbar-foreground/85 transition-colors hover:bg-white/20 md:flex"
          >
            <Search className="size-3.5" />
            <span>Buscar</span>
            <kbd className="rounded-md bg-black/15 px-1.5 py-0.5 text-[10px] font-semibold">
              Ctrl K
            </kbd>
          </button>
          <Button
            variant="ghost"
            size="icon"
            className="text-topbar-foreground hover:bg-white/15 hover:text-topbar-foreground md:hidden"
            onClick={() => setPaletteOpen(true)}
            aria-label="Buscar"
          >
            <Search className="size-4" />
          </Button>

          <Button
            size="sm"
            onClick={() => openEntry("EXPENSE")}
            className="ml-1.5 hidden bg-white font-semibold text-primary shadow-sm hover:bg-white/90 lg:inline-flex dark:text-[oklch(0.3_0.08_148)]"
          >
            <Plus className="size-4" /> Novo
          </Button>

          <NotificationBell triggerClassName="text-topbar-foreground hover:bg-white/15 hover:text-topbar-foreground" />
          <UserMenu balancesHidden={balances.hidden} onToggleBalances={balances.toggle} />
        </div>
      </header>

      <main className="mx-auto max-w-7xl overflow-x-hidden px-4 pt-5 pb-28 sm:px-6 lg:pt-7 lg:pb-12">
        <Outlet />
      </main>

      <MobileTabBar
        onNew={() => openEntry("EXPENSE")}
        onMore={() => setMoreOpen(true)}
        moreActive={moreActive}
      />
      <MoreSheet open={moreOpen} onOpenChange={setMoreOpen} />
      <CommandPalette
        open={paletteOpen}
        onOpenChange={setPaletteOpen}
        onToggleBalances={balances.toggle}
      />
    </div>
  );
}

function BrandMark() {
  // Monograma próprio (nada do logo do produto de referência): "f" sobre um
  // círculo claro com um corte diagonal — lê como moeda e como gráfico subindo.
  return (
    <svg viewBox="0 0 32 32" className="size-7" aria-hidden>
      <circle cx="16" cy="16" r="15" fill="white" />
      <path
        d="M6 22 L14 15 L18 18 L26 10"
        stroke="var(--color-topbar)"
        strokeWidth="2.6"
        fill="none"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <circle cx="26" cy="10" r="2.2" fill="var(--color-topbar)" />
    </svg>
  );
}

const topLinkClass = ({ isActive }: { isActive: boolean }) =>
  cn(
    "inline-flex items-center rounded-full px-3 py-1.5 text-sm font-medium whitespace-nowrap text-topbar-foreground/80 transition-colors hover:bg-white/10 hover:text-topbar-foreground",
    isActive && "bg-white/18 text-topbar-foreground",
  );

function MenuLink({ item, onClick }: { item: NavItem; onClick?: () => void }) {
  return (
    <NavLink
      to={item.to}
      onClick={onClick}
      className={({ isActive }) =>
        cn(
          "flex items-center gap-2.5 rounded-md px-2.5 py-2 text-sm transition-colors hover:bg-accent",
          isActive && "bg-accent font-semibold text-primary",
        )
      }
    >
      <item.icon className="size-4 text-muted-foreground" />
      {item.label}
    </NavLink>
  );
}

const THEME_OPTIONS: { value: ThemePreference; label: string; icon: LucideIcon }[] = [
  { value: "light", label: "Claro", icon: Sun },
  { value: "dark", label: "Escuro", icon: Moon },
  { value: "system", label: "Sistema", icon: Monitor },
];

function UserMenu({
  balancesHidden,
  onToggleBalances,
}: {
  balancesHidden: boolean;
  onToggleBalances: () => void;
}) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const { preference, setPreference } = useTheme();
  const initial = (user?.email ?? "?").charAt(0).toUpperCase();

  async function handleLogout() {
    await logout();
    void navigate("/login", { replace: true });
  }

  return (
    <Popover>
      <PopoverTrigger asChild>
        <button
          type="button"
          aria-label="Menu da conta"
          className="ml-1 flex size-8 items-center justify-center rounded-full bg-white text-sm font-bold text-primary ring-2 ring-white/25 transition hover:ring-white/50 dark:text-[oklch(0.3_0.08_148)]"
        >
          {initial}
        </button>
      </PopoverTrigger>
      <PopoverContent className="w-72 p-0">
        <div className="border-b border-border px-4 py-3">
          <div className="text-xs text-muted-foreground">Conectado como</div>
          <div className="truncate text-sm font-semibold">{user?.email}</div>
          {user?.mfaEnabled ? (
            <div className="mt-1 inline-flex items-center gap-1 text-xs font-medium text-success">
              <ShieldCheck className="size-3.5" /> Verificação em duas etapas ativa
            </div>
          ) : (
            <NavLink
              to="/configuracoes/seguranca"
              className="mt-1 inline-flex items-center gap-1 text-xs font-medium text-warning hover:underline"
            >
              <ShieldCheck className="size-3.5" /> Proteja sua conta com 2FA
            </NavLink>
          )}
        </div>
        <div className="p-1.5">
          <button
            type="button"
            onClick={onToggleBalances}
            className="flex w-full items-center gap-2.5 rounded-md px-2.5 py-2 text-left text-sm hover:bg-accent"
          >
            {balancesHidden ? (
              <Eye className="size-4 text-muted-foreground" />
            ) : (
              <EyeOff className="size-4 text-muted-foreground" />
            )}
            {balancesHidden ? "Mostrar saldos" : "Ocultar saldos"}
          </button>
          <div className="px-2.5 pt-2 pb-1 text-xs text-muted-foreground">Tema</div>
          <div
            className="mx-1.5 mb-1.5 grid grid-cols-3 gap-1 rounded-lg bg-muted p-1"
            role="radiogroup"
            aria-label="Tema"
          >
            {THEME_OPTIONS.map((option) => (
              <button
                key={option.value}
                type="button"
                role="radio"
                aria-checked={preference === option.value}
                onClick={() => setPreference(option.value)}
                className={cn(
                  "flex items-center justify-center gap-1 rounded-md py-1.5 text-xs font-medium transition-all",
                  preference === option.value
                    ? "bg-card text-foreground shadow-card"
                    : "text-muted-foreground hover:text-foreground",
                )}
              >
                <option.icon className="size-3.5" /> {option.label}
              </button>
            ))}
          </div>
          <MenuLink item={{ label: "Configurações", to: "/configuracoes", icon: Settings }} />
          <MenuLink
            item={{
              label: "Segurança da conta",
              to: "/configuracoes/seguranca",
              icon: ShieldCheck,
            }}
          />
        </div>
        <div className="border-t border-border p-1.5">
          <button
            type="button"
            onClick={() => void handleLogout()}
            className="flex w-full items-center gap-2.5 rounded-md px-2.5 py-2 text-left text-sm text-destructive hover:bg-destructive/10"
          >
            <LogOut className="size-4" /> Sair
          </button>
        </div>
      </PopoverContent>
    </Popover>
  );
}

const TAB_ITEMS: NavItem[] = [
  { label: "Início", to: "/", icon: LayoutDashboard },
  { label: "Lançamentos", to: "/lancamentos", icon: ListOrdered },
];
const TAB_ITEMS_RIGHT: NavItem[] = [{ label: "Relatórios", to: "/relatorios", icon: BarChart3 }];

function MobileTabBar({
  onNew,
  onMore,
  moreActive,
}: {
  onNew: () => void;
  onMore: () => void;
  moreActive: boolean;
}) {
  const tab = ({ isActive }: { isActive: boolean }) =>
    cn(
      "flex flex-1 flex-col items-center gap-0.5 pt-2 pb-1 text-[11px] font-medium text-muted-foreground transition-colors",
      isActive && "text-primary",
    );

  return (
    <nav
      aria-label="Navegação inferior"
      className="fixed inset-x-0 bottom-0 z-40 border-t border-border bg-card/95 pb-[env(safe-area-inset-bottom)] backdrop-blur-md lg:hidden"
    >
      <div className="mx-auto flex max-w-md items-end">
        {TAB_ITEMS.map((item) => (
          <NavLink key={item.to} to={item.to} end={item.to === "/"} className={tab}>
            <item.icon className="size-5" />
            {item.label}
          </NavLink>
        ))}
        <div className="flex flex-1 justify-center">
          <button
            type="button"
            onClick={onNew}
            aria-label="Novo lançamento"
            className="-mt-5 mb-1 flex size-14 items-center justify-center rounded-full bg-primary text-primary-foreground shadow-float ring-4 ring-background transition-transform active:scale-95"
          >
            <Plus className="size-6" strokeWidth={2.5} />
          </button>
        </div>
        {TAB_ITEMS_RIGHT.map((item) => (
          <NavLink key={item.to} to={item.to} className={tab}>
            <item.icon className="size-5" />
            {item.label}
          </NavLink>
        ))}
        <button type="button" onClick={onMore} className={tab({ isActive: moreActive })}>
          <Menu className="size-5" />
          Mais
        </button>
      </div>
    </nav>
  );
}

function MoreSheet({
  open,
  onOpenChange,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  return (
    <DialogPrimitive.Root open={open} onOpenChange={onOpenChange}>
      <DialogPrimitive.Portal>
        <DialogPrimitive.Overlay className="fixed inset-0 z-50 animate-fade bg-black/40" />
        <DialogPrimitive.Content
          aria-describedby={undefined}
          className="fixed inset-x-0 bottom-0 z-50 animate-rise rounded-t-3xl border-t border-border bg-card p-4 pb-[calc(1rem+env(safe-area-inset-bottom))] shadow-float"
        >
          <div className="mx-auto mb-3 h-1 w-10 rounded-full bg-muted" />
          <DialogPrimitive.Title className="mb-2 px-1 text-sm font-semibold">
            Todas as seções
          </DialogPrimitive.Title>
          <div className="grid grid-cols-3 gap-2">
            {ALL_NAV.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.to === "/"}
                onClick={() => onOpenChange(false)}
                className={({ isActive }) =>
                  cn(
                    "flex flex-col items-center gap-1.5 rounded-2xl border border-border/60 bg-surface-2 px-2 py-3 text-center text-xs font-medium",
                    isActive && "border-primary/40 bg-primary/10 text-primary",
                  )
                }
              >
                <item.icon className="size-5" />
                {item.label}
              </NavLink>
            ))}
          </div>
        </DialogPrimitive.Content>
      </DialogPrimitive.Portal>
    </DialogPrimitive.Root>
  );
}
