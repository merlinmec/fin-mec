import { useState } from "react";
import type { Account } from "@/api/accounts";
import { ACCOUNT_TYPE_LABELS } from "@/api/accounts";
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
    <tr className="border-b border-border last:border-0">
      <td className="py-3 pr-4 pl-4">
        <div className="font-medium">{account.name}</div>
        {account.archived && (
          <span className="mt-0.5 inline-block rounded-full bg-muted px-2 py-0.5 text-xs text-muted-foreground">
            Arquivada
          </span>
        )}
      </td>
      <td className="py-3 pr-4 text-muted-foreground">{ACCOUNT_TYPE_LABELS[account.type]}</td>
      <td className="py-3 pr-4 text-right tabular-nums">{formatMoney(account.initialBalance)}</td>
      <td className="py-3 pr-4 text-right">
        <div className="flex justify-end gap-2">
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
      </td>
    </tr>
  );
}
