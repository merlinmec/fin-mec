import { useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { Receipt } from "lucide-react";
import type { Transaction, TransactionType } from "@/api/transactions";
import { TRANSACTION_TYPE_LABELS } from "@/api/transactions";
import { useAccounts } from "@/hooks/useAccounts";
import { useCategories } from "@/hooks/useCategories";
import { AccountSelect } from "@/components/AccountSelect";
import { CategorySelect } from "@/components/CategorySelect";
import { EmptyState } from "@/components/EmptyState";
import { MonthSelector } from "@/components/MonthSelector";
import { TableSkeleton } from "@/components/TableSkeleton";
import { Button } from "@/components/ui/button";
import { NativeSelect } from "@/components/ui/native-select";
import { currentYearMonth, formatYearMonth } from "@/lib/dates";
import { useTransactions } from "./hooks";
import { TransactionRow } from "./TransactionRow";
import { TransactionFormDialog } from "./TransactionFormDialog";
import { TransferFormDialog } from "./TransferFormDialog";
import { InstallmentFormDialog } from "./InstallmentFormDialog";

const PAGE_SIZE = 20;

export function TransactionsPage() {
  // Seed unico a partir da URL (drill-down do Dashboard: "categoria" -> aqui filtrado) — nao
  // sincroniza de volta pra URL, e so um ponto de entrada, os filtros continuam locais depois.
  const [searchParams] = useSearchParams();

  const [month, setMonth] = useState(currentYearMonth());
  const [accountId, setAccountId] = useState<string | undefined>(undefined);
  const [categoryId, setCategoryId] = useState<string | undefined>(searchParams.get("categoryId") ?? undefined);
  const [type, setType] = useState<TransactionType | "">("");
  const [page, setPage] = useState(0);

  const [entryOpen, setEntryOpen] = useState(false);
  const [editingTransaction, setEditingTransaction] = useState<Transaction | undefined>(undefined);
  const [transferOpen, setTransferOpen] = useState(false);
  const [installmentOpen, setInstallmentOpen] = useState(false);

  const { data: accounts } = useAccounts();
  const { data: categories } = useCategories();
  const accountsById = useMemo(() => new Map((accounts ?? []).map((a) => [a.id, a])), [accounts]);
  const categoriesById = useMemo(() => new Map((categories ?? []).map((c) => [c.id, c])), [categories]);

  const { data, isPending, isError, isPlaceholderData } = useTransactions({
    competenceMonth: month,
    accountId,
    categoryId,
    type: type || undefined,
    page,
    size: PAGE_SIZE,
  });

  function changeMonth(next: string) {
    setMonth(next);
    setPage(0);
  }

  function openCreate() {
    setEditingTransaction(undefined);
    setEntryOpen(true);
  }

  function openEdit(transaction: Transaction) {
    setEditingTransaction(transaction);
    setEntryOpen(true);
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-xl font-semibold tracking-tight">Lançamentos</h1>
        <div className="flex flex-wrap gap-2">
          <Button variant="outline" onClick={() => setTransferOpen(true)}>
            Transferência
          </Button>
          <Button variant="outline" onClick={() => setInstallmentOpen(true)}>
            Parcelamento
          </Button>
          <Button onClick={openCreate}>Novo lançamento</Button>
        </div>
      </div>

      <div className="flex flex-wrap items-end gap-3 rounded-xl border border-border bg-card p-4">
        <MonthSelector value={month} onChange={changeMonth} />

        <div className="min-w-48 flex-1 space-y-1">
          <span className="text-xs text-muted-foreground">Conta</span>
          <AccountSelect
            value={accountId}
            onValueChange={(v) => {
              setAccountId(v);
              setPage(0);
            }}
            includeArchived
            clearable
          />
        </div>

        <div className="min-w-48 flex-1 space-y-1">
          <span className="text-xs text-muted-foreground">Categoria</span>
          <CategorySelect
            value={categoryId}
            onValueChange={(v) => {
              setCategoryId(v);
              setPage(0);
            }}
            clearable
            clearLabel="Todas as categorias"
          />
        </div>

        <div className="min-w-40 space-y-1">
          <span className="text-xs text-muted-foreground">Tipo</span>
          <NativeSelect
            value={type}
            onChange={(e) => {
              setType(e.target.value as TransactionType | "");
              setPage(0);
            }}
          >
            <option value="">Todos os tipos</option>
            {(Object.keys(TRANSACTION_TYPE_LABELS) as TransactionType[]).map((t) => (
              <option key={t} value={t}>
                {TRANSACTION_TYPE_LABELS[t]}
              </option>
            ))}
          </NativeSelect>
        </div>
      </div>

      {isPending && <TableSkeleton columns={5} />}

      {isError && (
        <p className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
          Não foi possível carregar os lançamentos. Tente recarregar a página.
        </p>
      )}

      {data && data.content.length === 0 && (
        <EmptyState icon={Receipt} title="Nenhum lançamento" description={`Nada em ${formatYearMonth(month)} com esses filtros.`} />
      )}

      {data && data.content.length > 0 && (
        <div className={isPlaceholderData ? "opacity-60 transition-opacity" : undefined}>
          <div className="overflow-x-auto rounded-xl border border-border">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-border text-left text-muted-foreground">
                  <th className="py-2 pr-4 pl-4 font-medium">Data</th>
                  <th className="py-2 pr-4 font-medium">Descrição</th>
                  <th className="py-2 pr-4 font-medium">Conta</th>
                  <th className="py-2 pr-4 text-right font-medium">Valor</th>
                  <th className="py-2 pr-4 font-medium">Status</th>
                  <th className="py-2 pr-4 font-medium" />
                </tr>
              </thead>
              <tbody>
                {data.content.map((transaction) => (
                  <TransactionRow
                    key={transaction.id}
                    transaction={transaction}
                    accountsById={accountsById}
                    categoriesById={categoriesById}
                    onEdit={openEdit}
                  />
                ))}
              </tbody>
            </table>
          </div>

          <div className="mt-3 flex flex-wrap items-center justify-between gap-2 text-sm text-muted-foreground">
            <span>{data.totalElements} lançamento(s)</span>
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

      <TransactionFormDialog open={entryOpen} onOpenChange={setEntryOpen} transaction={editingTransaction} />
      <TransferFormDialog open={transferOpen} onOpenChange={setTransferOpen} />
      <InstallmentFormDialog open={installmentOpen} onOpenChange={setInstallmentOpen} />
    </div>
  );
}
