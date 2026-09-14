import { useState } from "react";
import { Wallet } from "lucide-react";
import type { Account } from "@/api/accounts";
import { formatMoney } from "@/lib/money";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/EmptyState";
import { TableSkeleton } from "@/components/TableSkeleton";
import { useAccounts } from "./hooks";
import { AccountRow } from "./AccountRow";
import { AccountFormDialog } from "./AccountFormDialog";

export function AccountsPage() {
  const { data: accounts, isPending, isError } = useAccounts();
  const [formOpen, setFormOpen] = useState(false);
  const [editingAccount, setEditingAccount] = useState<Account | undefined>(undefined);

  function openCreate() {
    setEditingAccount(undefined);
    setFormOpen(true);
  }

  function openEdit(account: Account) {
    setEditingAccount(account);
    setFormOpen(true);
  }

  // Saldo total considera so contas ativas — uma arquivada e, na pratica, uma
  // conta fechada/inativa, entao nao deveria compor o total disponivel hoje.
  const total = accounts?.filter((a) => !a.archived).reduce((sum, a) => sum + a.initialBalance, 0) ?? 0;

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold tracking-tight">Contas</h1>
          <p className="text-sm text-muted-foreground">
            Saldo total: <span className="font-medium text-foreground">{formatMoney(total)}</span>
          </p>
        </div>
        <Button onClick={openCreate}>Nova conta</Button>
      </div>

      {isPending && <TableSkeleton columns={4} />}

      {isError && (
        <p className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
          Não foi possível carregar as contas. Tente recarregar a página.
        </p>
      )}

      {accounts && accounts.length === 0 && (
        <EmptyState
          icon={Wallet}
          title="Nenhuma conta ainda"
          description="Crie a primeira pra começar a organizar suas finanças."
        />
      )}

      {accounts && accounts.length > 0 && (
        <div className="overflow-x-auto rounded-xl border border-border">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-border text-left text-muted-foreground">
                <th className="py-2 pr-4 pl-4 font-medium">Nome</th>
                <th className="py-2 pr-4 font-medium">Tipo</th>
                <th className="py-2 pr-4 text-right font-medium">Saldo inicial</th>
                <th className="py-2 pr-4 font-medium" />
              </tr>
            </thead>
            <tbody className="px-4">
              {accounts.map((account) => (
                <AccountRow key={account.id} account={account} onEdit={openEdit} />
              ))}
            </tbody>
          </table>
        </div>
      )}

      <AccountFormDialog open={formOpen} onOpenChange={setFormOpen} account={editingAccount} />
    </div>
  );
}
