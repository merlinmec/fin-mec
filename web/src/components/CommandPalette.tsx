import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Command } from "cmdk";
import * as DialogPrimitive from "@radix-ui/react-dialog";
import {
  ArrowLeftRight,
  BarChart3,
  CalendarClock,
  CreditCard,
  Eye,
  FileUp,
  Landmark,
  LayoutDashboard,
  ListOrdered,
  Minus,
  Moon,
  PiggyBank,
  Plus,
  Search,
  Settings,
  ShieldCheck,
  Shapes,
  Target,
  Wallet,
  type LucideIcon,
} from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { searchTransactions } from "@/api/transactions";
import { useQuickAdd } from "@/app/quick-add";
import { useTheme } from "@/lib/theme";
import { formatDate } from "@/lib/dates";
import { formatMoney } from "@/lib/money";
import { cn } from "@/lib/utils";

interface CommandPaletteProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onToggleBalances: () => void;
}

const PAGES: { label: string; to: string; icon: LucideIcon; keywords?: string }[] = [
  { label: "Dashboard", to: "/", icon: LayoutDashboard, keywords: "inicio resumo" },
  { label: "Lançamentos", to: "/lancamentos", icon: ListOrdered, keywords: "extrato transacoes" },
  { label: "Relatórios", to: "/relatorios", icon: BarChart3, keywords: "graficos fluxo de caixa" },
  { label: "Contas", to: "/contas", icon: Wallet, keywords: "bancos carteira" },
  { label: "Cartões", to: "/cartoes", icon: CreditCard, keywords: "fatura credito" },
  {
    label: "Contas a pagar",
    to: "/contas-a-pagar",
    icon: CalendarClock,
    keywords: "boletos vencimentos",
  },
  { label: "Orçamento", to: "/orcamento", icon: PiggyBank, keywords: "limite planejamento" },
  { label: "Metas", to: "/metas", icon: Target, keywords: "objetivos economia cofrinho" },
  {
    label: "Bancos conectados",
    to: "/bancos",
    icon: Landmark,
    keywords: "open finance pluggy sincronizar conectar banco",
  },
  { label: "Importar extrato", to: "/importar", icon: FileUp, keywords: "ofx csv banco upload" },
  { label: "Categorias", to: "/categorias", icon: Shapes },
  {
    label: "Configurações",
    to: "/configuracoes",
    icon: Settings,
    keywords: "tags fixos aparencia",
  },
  {
    label: "Segurança da conta",
    to: "/configuracoes/seguranca",
    icon: ShieldCheck,
    keywords: "senha 2fa autenticacao",
  },
];

function useDebounced<T>(value: T, delay: number): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const id = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(id);
  }, [value, delay]);
  return debounced;
}

/**
 * Paleta de comandos (Ctrl/⌘+K): navega, dispara ações e busca lançamentos
 * pela descrição (busca no backend, a mesma do filtro de Lançamentos).
 */
export function CommandPalette({ open, onOpenChange, onToggleBalances }: CommandPaletteProps) {
  const navigate = useNavigate();
  const { openEntry, openTransfer } = useQuickAdd();
  const { toggle: toggleTheme, resolved } = useTheme();
  const [query, setQuery] = useState("");
  const debounced = useDebounced(query.trim(), 250);

  const { data: matches, isFetching } = useQuery({
    queryKey: ["transactions", "palette", debounced],
    queryFn: () => searchTransactions({ q: debounced, size: 6 }),
    enabled: open && debounced.length >= 2,
    staleTime: 30_000,
  });

  useEffect(() => {
    if (!open) setQuery("");
  }, [open]);

  function run(action: () => void) {
    onOpenChange(false);
    action();
  }

  return (
    <DialogPrimitive.Root open={open} onOpenChange={onOpenChange}>
      <DialogPrimitive.Portal>
        <DialogPrimitive.Overlay className="fixed inset-0 z-50 animate-fade bg-black/40 backdrop-blur-[2px]" />
        <DialogPrimitive.Content
          aria-describedby={undefined}
          className="fixed top-[12dvh] left-1/2 z-50 w-[calc(100%-2rem)] max-w-xl -translate-x-1/2 animate-rise overflow-hidden rounded-2xl border border-border bg-popover text-popover-foreground shadow-float"
        >
          <DialogPrimitive.Title className="sr-only">Paleta de comandos</DialogPrimitive.Title>
          <Command label="Paleta de comandos" shouldFilter={true} loop>
            <div className="flex items-center gap-2 border-b border-border px-4">
              <Search className="size-4 shrink-0 text-muted-foreground" />
              <Command.Input
                value={query}
                onValueChange={setQuery}
                placeholder="Buscar lançamentos, telas ou ações…"
                className="h-12 w-full bg-transparent text-sm outline-none placeholder:text-muted-foreground"
              />
              <kbd className="hidden rounded border border-border px-1.5 py-0.5 text-[10px] text-muted-foreground sm:inline">
                ESC
              </kbd>
            </div>
            <Command.List className="max-h-[min(60dvh,420px)] overflow-y-auto p-2">
              <Command.Empty className="px-3 py-8 text-center text-sm text-muted-foreground">
                {isFetching ? "Buscando…" : "Nada encontrado."}
              </Command.Empty>

              {matches && matches.content.length > 0 && (
                <Group heading="Lançamentos">
                  {matches.content.map((t) => {
                    const outflow = t.type === "EXPENSE" || t.transferDirection === "OUT";
                    return (
                      <Item
                        key={t.id}
                        value={`lancamento ${t.id} ${t.description} ${query}`}
                        onSelect={() =>
                          run(
                            () =>
                              void navigate(`/lancamentos?q=${encodeURIComponent(t.description)}`),
                          )
                        }
                      >
                        <span
                          className={cn(
                            "size-2 shrink-0 rounded-full",
                            outflow ? "bg-destructive" : "bg-success",
                          )}
                        />
                        <span className="flex-1 truncate">{t.description}</span>
                        <span className="text-xs text-muted-foreground">
                          {formatDate(t.transactionDate)}
                        </span>
                        <span
                          className={cn(
                            "num w-24 text-right text-xs font-semibold",
                            outflow ? "text-destructive" : "text-success",
                          )}
                        >
                          {outflow ? "−" : "+"}
                          {formatMoney(t.amount)}
                        </span>
                      </Item>
                    );
                  })}
                  <Item
                    value={`ver todos resultados ${query}`}
                    onSelect={() =>
                      run(() => void navigate(`/lancamentos?q=${encodeURIComponent(query.trim())}`))
                    }
                  >
                    <Search className="size-4 text-muted-foreground" />
                    Ver todos os resultados para “{query.trim()}”
                  </Item>
                </Group>
              )}

              <Group heading="Ações">
                <Item
                  value="nova despesa lancamento gasto"
                  onSelect={() => run(() => openEntry("EXPENSE"))}
                  shortcut="N"
                >
                  <Minus className="size-4 text-destructive" /> Nova despesa
                </Item>
                <Item
                  value="nova receita lancamento entrada"
                  onSelect={() => run(() => openEntry("INCOME"))}
                >
                  <Plus className="size-4 text-success" /> Nova receita
                </Item>
                <Item value="nova transferencia entre contas" onSelect={() => run(openTransfer)}>
                  <ArrowLeftRight className="size-4 text-muted-foreground" /> Nova transferência
                </Item>
                <Item value="alternar tema escuro claro" onSelect={() => run(toggleTheme)}>
                  <Moon className="size-4 text-muted-foreground" />{" "}
                  {resolved === "dark" ? "Usar tema claro" : "Usar tema escuro"}
                </Item>
                <Item value="ocultar mostrar saldos valores" onSelect={() => run(onToggleBalances)}>
                  <Eye className="size-4 text-muted-foreground" /> Ocultar/mostrar saldos
                </Item>
              </Group>

              <Group heading="Ir para">
                {PAGES.map((page) => (
                  <Item
                    key={page.to}
                    value={`${page.label} ${page.keywords ?? ""}`}
                    onSelect={() => run(() => void navigate(page.to))}
                  >
                    <page.icon className="size-4 text-muted-foreground" /> {page.label}
                  </Item>
                ))}
              </Group>
            </Command.List>
          </Command>
        </DialogPrimitive.Content>
      </DialogPrimitive.Portal>
    </DialogPrimitive.Root>
  );
}

function Group({ heading, children }: { heading: string; children: React.ReactNode }) {
  return (
    <Command.Group
      heading={heading}
      className="mb-1 [&_[cmdk-group-heading]]:px-2 [&_[cmdk-group-heading]]:pt-2 [&_[cmdk-group-heading]]:pb-1 [&_[cmdk-group-heading]]:text-[11px] [&_[cmdk-group-heading]]:font-semibold [&_[cmdk-group-heading]]:tracking-wide [&_[cmdk-group-heading]]:text-muted-foreground [&_[cmdk-group-heading]]:uppercase"
    >
      {children}
    </Command.Group>
  );
}

function Item({
  children,
  value,
  onSelect,
  shortcut,
}: {
  children: React.ReactNode;
  value: string;
  onSelect: () => void;
  shortcut?: string;
}) {
  return (
    <Command.Item
      value={value}
      onSelect={onSelect}
      className="flex cursor-pointer items-center gap-2.5 rounded-lg px-2.5 py-2 text-sm outline-none select-none data-[selected=true]:bg-accent data-[selected=true]:text-accent-foreground"
    >
      {children}
      {shortcut && (
        <kbd className="ml-auto rounded border border-border px-1.5 py-0.5 text-[10px] text-muted-foreground">
          {shortcut}
        </kbd>
      )}
    </Command.Item>
  );
}
