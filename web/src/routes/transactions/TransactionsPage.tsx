import { useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { Download, Receipt, Search, SlidersHorizontal, X } from "lucide-react";
import type {
  Transaction,
  TransactionSearchParams,
  TransactionStatus,
  TransactionType,
} from "@/api/transactions";
import {
  TRANSACTION_STATUS_LABELS,
  TRANSACTION_TYPE_LABELS,
  exportTransactionsUrl,
} from "@/api/transactions";
import { useAccounts } from "@/hooks/useAccounts";
import { useCategories } from "@/hooks/useCategories";
import { useTags } from "@/hooks/useFeatureData";
import { AccountSelect } from "@/components/AccountSelect";
import { CategorySelect } from "@/components/CategorySelect";
import { EmptyState } from "@/components/EmptyState";
import { MonthSelector } from "@/components/MonthSelector";
import { Button } from "@/components/ui/button";
import { buttonVariants } from "@/components/ui/button-variants";
import { Input } from "@/components/ui/input";
import { NativeSelect } from "@/components/ui/native-select";
import { PageHeader } from "@/components/ui/panel";
import { Skeleton } from "@/components/ui/skeleton";
import { currentYearMonth, formatYearMonth } from "@/lib/dates";
import { cn } from "@/lib/utils";
import { useTransactions } from "./hooks";
import { TransactionRow } from "./TransactionRow";
import { TransactionFormDialog } from "./TransactionFormDialog";
import { TransferFormDialog } from "./TransferFormDialog";
import { InstallmentFormDialog } from "./InstallmentFormDialog";

const PAGE_SIZE = 30;

function useDebounced<T>(value: T, delay: number): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const id = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(id);
  }, [value, delay]);
  return debounced;
}

/** yyyy-MM-dd -> "Seg, 28 de setembro" (cabeçalho do grupo do dia). */
function dayHeading(iso: string): string {
  const [y, m, d] = iso.split("-").map(Number);
  const label = new Date(y, m - 1, d).toLocaleDateString("pt-BR", {
    weekday: "short",
    day: "numeric",
    month: "long",
  });
  return label.charAt(0).toUpperCase() + label.slice(1).replace(".", "");
}

function groupByDay(transactions: Transaction[]): [string, Transaction[]][] {
  const groups = new Map<string, Transaction[]>();
  for (const t of transactions) {
    const list = groups.get(t.transactionDate) ?? [];
    list.push(t);
    groups.set(t.transactionDate, list);
  }
  return [...groups.entries()];
}

export function TransactionsPage() {
  // Ponto de entrada via URL (drill-down do dashboard, paleta de comandos) — só semeia os
  // filtros; depois eles vivem no estado da tela.
  const [searchParams] = useSearchParams();

  const [month, setMonth] = useState(currentYearMonth());
  const [query, setQuery] = useState(searchParams.get("q") ?? "");
  const [accountId, setAccountId] = useState<string | undefined>(undefined);
  const [categoryId, setCategoryId] = useState<string | undefined>(
    searchParams.get("categoryId") ?? undefined,
  );
  const [type, setType] = useState<TransactionType | "">("");
  const [status, setStatus] = useState<TransactionStatus | "">(
    (searchParams.get("status") as TransactionStatus) ?? "",
  );
  const [tagId, setTagId] = useState<string>("");
  const [minAmount, setMinAmount] = useState("");
  const [maxAmount, setMaxAmount] = useState("");
  const [showFilters, setShowFilters] = useState(false);
  const [page, setPage] = useState(0);

  const [entryOpen, setEntryOpen] = useState(false);
  const [editingTransaction, setEditingTransaction] = useState<Transaction | undefined>(undefined);
  const [transferOpen, setTransferOpen] = useState(false);
  const [installmentOpen, setInstallmentOpen] = useState(false);

  const { data: accounts } = useAccounts();
  const { data: categories } = useCategories();
  const { data: tags } = useTags();
  const accountsById = useMemo(() => new Map((accounts ?? []).map((a) => [a.id, a])), [accounts]);
  const categoriesById = useMemo(
    () => new Map((categories ?? []).map((c) => [c.id, c])),
    [categories],
  );
  const tagsById = useMemo(() => new Map((tags ?? []).map((t) => [t.id, t])), [tags]);

  const q = useDebounced(query.trim(), 300);
  // Buscando por texto, procura em todos os meses — quem digita "Netflix" quer o histórico.
  const searchingAllMonths = q.length > 0;

  const filters: Omit<TransactionSearchParams, "page" | "size"> = {
    competenceMonth: searchingAllMonths ? undefined : month,
    q: q || undefined,
    accountId,
    categoryId,
    type: type || undefined,
    status: status || undefined,
    tagId: tagId || undefined,
    minAmount: minAmount ? Number(minAmount) : undefined,
    maxAmount: maxAmount ? Number(maxAmount) : undefined,
  };
  const { data, isPending, isError, isPlaceholderData } = useTransactions({
    ...filters,
    page,
    size: PAGE_SIZE,
  });

  useEffect(
    () => setPage(0),
    [q, month, accountId, categoryId, type, status, tagId, minAmount, maxAmount],
  );

  const advancedCount = [accountId, categoryId, type, status, tagId, minAmount, maxAmount].filter(
    Boolean,
  ).length;

  function clearFilters() {
    setAccountId(undefined);
    setCategoryId(undefined);
    setType("");
    setStatus("");
    setTagId("");
    setMinAmount("");
    setMaxAmount("");
  }

  function openEdit(transaction: Transaction) {
    setEditingTransaction(transaction);
    setEntryOpen(true);
  }

  return (
    <div className="space-y-5">
      <PageHeader
        title="Lançamentos"
        description="Receitas, despesas, transferências e parcelas — busque em todo o histórico."
        actions={
          <>
            <a
              href={exportTransactionsUrl(filters)}
              download
              className={buttonVariants({ variant: "outline" })}
            >
              <Download /> Exportar CSV
            </a>
            <Button variant="outline" onClick={() => setTransferOpen(true)}>
              Transferência
            </Button>
            <Button variant="outline" onClick={() => setInstallmentOpen(true)}>
              Parcelamento
            </Button>
            {/* No celular o botão flutuante da barra inferior já faz isso. */}
            <Button
              className="hidden sm:inline-flex"
              onClick={() => {
                setEditingTransaction(undefined);
                setEntryOpen(true);
              }}
            >
              Novo lançamento
            </Button>
          </>
        }
      />

      <div className="space-y-3 rounded-2xl border border-border/60 bg-card p-3 shadow-card sm:p-4">
        <div className="flex flex-wrap items-center gap-2">
          <div className="relative min-w-56 flex-1">
            <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Buscar pela descrição (ex.: mercado, netflix)…"
              className="h-10 pl-9"
              maxLength={100}
              aria-label="Buscar lançamentos"
            />
            {query && (
              <button
                type="button"
                onClick={() => setQuery("")}
                aria-label="Limpar busca"
                className="absolute top-1/2 right-2 -translate-y-1/2 rounded-full p-1 text-muted-foreground hover:bg-accent"
              >
                <X className="size-3.5" />
              </button>
            )}
          </div>
          {searchingAllMonths ? (
            <span className="rounded-full bg-primary/10 px-3 py-1.5 text-xs font-semibold text-primary">
              Em todos os meses
            </span>
          ) : (
            <MonthSelector value={month} onChange={setMonth} />
          )}
          <Button
            variant={showFilters || advancedCount ? "secondary" : "outline"}
            onClick={() => setShowFilters((v) => !v)}
            aria-expanded={showFilters}
          >
            <SlidersHorizontal /> Filtros
            {advancedCount > 0 && (
              <span className="rounded-full bg-primary px-1.5 text-[10px] font-bold text-primary-foreground">
                {advancedCount}
              </span>
            )}
          </Button>
        </div>

        {showFilters && (
          <div className="grid animate-rise gap-3 border-t border-border/60 pt-3 sm:grid-cols-2 lg:grid-cols-4">
            <Field label="Conta">
              <AccountSelect
                value={accountId}
                onValueChange={setAccountId}
                includeArchived
                clearable
              />
            </Field>
            <Field label="Categoria">
              <CategorySelect
                value={categoryId}
                onValueChange={setCategoryId}
                clearable
                clearLabel="Todas as categorias"
              />
            </Field>
            <Field label="Tipo">
              <NativeSelect
                value={type}
                onChange={(e) => setType(e.target.value as TransactionType | "")}
              >
                <option value="">Todos os tipos</option>
                {(Object.keys(TRANSACTION_TYPE_LABELS) as TransactionType[]).map((t) => (
                  <option key={t} value={t}>
                    {TRANSACTION_TYPE_LABELS[t]}
                  </option>
                ))}
              </NativeSelect>
            </Field>
            <Field label="Situação">
              <NativeSelect
                value={status}
                onChange={(e) => setStatus(e.target.value as TransactionStatus | "")}
              >
                <option value="">Todas</option>
                {(Object.keys(TRANSACTION_STATUS_LABELS) as TransactionStatus[]).map((s) => (
                  <option key={s} value={s}>
                    {TRANSACTION_STATUS_LABELS[s]}
                  </option>
                ))}
              </NativeSelect>
            </Field>
            <Field label="Tag">
              <NativeSelect value={tagId} onChange={(e) => setTagId(e.target.value)}>
                <option value="">Todas as tags</option>
                {(tags ?? []).map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.name}
                  </option>
                ))}
              </NativeSelect>
            </Field>
            <Field label="Valor mínimo">
              <Input
                type="number"
                inputMode="decimal"
                min={0}
                step="0.01"
                value={minAmount}
                onChange={(e) => setMinAmount(e.target.value)}
                placeholder="R$ 0,00"
              />
            </Field>
            <Field label="Valor máximo">
              <Input
                type="number"
                inputMode="decimal"
                min={0}
                step="0.01"
                value={maxAmount}
                onChange={(e) => setMaxAmount(e.target.value)}
                placeholder="Sem limite"
              />
            </Field>
            <div className="flex items-end">
              <Button
                variant="ghost"
                onClick={clearFilters}
                disabled={advancedCount === 0}
                className="w-full"
              >
                Limpar filtros
              </Button>
            </div>
          </div>
        )}
      </div>

      {isPending && (
        <div className="space-y-2">
          {Array.from({ length: 6 }).map((_, i) => (
            <Skeleton key={i} className="h-16 rounded-xl" />
          ))}
        </div>
      )}

      {isError && (
        <p className="rounded-xl bg-destructive/10 px-4 py-3 text-sm text-destructive">
          Não foi possível carregar os lançamentos. Verifique os filtros ou recarregue a página.
        </p>
      )}

      {data && data.content.length === 0 && (
        <EmptyState
          icon={Receipt}
          title={q ? `Nada encontrado para “${q}”` : "Nenhum lançamento"}
          description={
            q
              ? "Tente outra palavra ou limpe os filtros."
              : `Nada em ${formatYearMonth(month).toLowerCase()} com esses filtros. Use “Novo lançamento” ou a tecla N.`
          }
        />
      )}

      {data && data.content.length > 0 && (
        <div className={cn("space-y-4", isPlaceholderData && "opacity-60 transition-opacity")}>
          {groupByDay(data.content).map(([day, items]) => (
            <section key={day}>
              <h2 className="mb-1.5 px-1 text-xs font-semibold tracking-wide text-muted-foreground uppercase">
                {dayHeading(day)}
              </h2>
              <ul className="divide-y divide-border/60 overflow-hidden rounded-2xl border border-border/60 bg-card shadow-card">
                {items.map((transaction) => (
                  <TransactionRow
                    key={transaction.id}
                    transaction={transaction}
                    accountsById={accountsById}
                    categoriesById={categoriesById}
                    tagsById={tagsById}
                    onEdit={openEdit}
                  />
                ))}
              </ul>
            </section>
          ))}

          <div className="flex flex-wrap items-center justify-between gap-2 text-sm text-muted-foreground">
            <span>
              {data.totalElements} lançamento{data.totalElements === 1 ? "" : "s"}
            </span>
            <div className="flex items-center gap-2">
              <Button
                variant="outline"
                size="sm"
                disabled={page === 0}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
              >
                Anterior
              </Button>
              <span>
                Página {data.page + 1} de {Math.max(1, data.totalPages)}
              </span>
              <Button
                variant="outline"
                size="sm"
                disabled={page + 1 >= data.totalPages}
                onClick={() => setPage((p) => p + 1)}
              >
                Próxima
              </Button>
            </div>
          </div>
        </div>
      )}

      <TransactionFormDialog
        open={entryOpen}
        onOpenChange={setEntryOpen}
        transaction={editingTransaction}
        defaultAccountId={accountId}
      />
      <TransferFormDialog open={transferOpen} onOpenChange={setTransferOpen} />
      <InstallmentFormDialog open={installmentOpen} onOpenChange={setInstallmentOpen} />
    </div>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="block space-y-1">
      <span className="text-xs font-medium text-muted-foreground">{label}</span>
      {children}
    </label>
  );
}
