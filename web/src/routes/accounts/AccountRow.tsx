import { useState } from "react";
import type { Account } from "@/api/accounts";
import { ACCOUNT_TYPE_LABELS } from "@/api/accounts";
import { AccountIcon } from "@/components/AccountIcon";
import { formatMoney } from "@/lib/money";
import { Button } from "@/components/ui/button";
import { useDeleteAccount } from "./hooks";

interface AccountRowProps {
  account: Account;
  onEdit: (account: Account) => void;
}

/**
 * Confirmacao de exclusao em duas etapas no proprio botao (sem AlertDialog
 * dedicado) — soft delete no backend, mas some da listagem, entao merece
 * uma trava contra clique acidental. Reavaliar se um AlertDialog real
 * (Radix) compensar quando mais telas precisarem do mesmo padrao.
 */
export function AccountRow({ account, onEdit }: AccountRowProps) {
  const [confirming, setConfirming] = useState(false);
  const deleteAccount = useDeleteAccount();

  return (
    <li className="flex flex-wrap items-center gap-3 px-4 py-3">
      <AccountIcon account={account} />
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2 font-medium">
          {account.name}
          {account.archived && (
            <span className="inline-block rounded-full bg-muted px-2 py-0.5 text-xs font-normal text-muted-foreground">
              Arquivada
            </span>
          )}
        </div>
        <div className="text-xs text-muted-foreground">{ACCOUNT_TYPE_LABELS[account.type]}</div>
      </div>
      <div className="font-semibold tabular-nums">{formatMoney(account.initialBalance)}</div>
      <div className="flex gap-2">
        <Button variant="outline" size="sm" onClick={() => onEdit(account)} disabled={confirming}>
          Editar
        </Button>
        {confirming ? (
          <>
            <Button
              variant="destructive"
              size="sm"
              disabled={deleteAccount.isPending}
              onClick={() => deleteAccount.mutate(account.id, { onSettled: () => setConfirming(false) })}
            >
              Confirmar
            </Button>
            <Button variant="ghost" size="sm" onClick={() => setConfirming(false)}>
              Cancelar
            </Button>
          </>
        ) : (
          <Button variant="outline" size="sm" onClick={() => setConfirming(true)}>
            Excluir
          </Button>
        )}
      </div>
    </li>
  );
}
