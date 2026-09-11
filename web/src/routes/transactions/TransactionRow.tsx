import { useState } from "react";
import { ArrowDownLeft, ArrowUpRight } from "lucide-react";
import type { Account } from "@/api/accounts";
import type { Category } from "@/api/categories";
import type { Transaction } from "@/api/transactions";
import { TRANSACTION_STATUS_LABELS } from "@/api/transactions";
import { CategoryIcon } from "@/components/CategoryIcon";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { formatDate } from "@/lib/dates";
import { formatMoney } from "@/lib/money";
import { useCancelTransaction } from "./hooks";

interface TransactionRowProps {
  transaction: Transaction;
  accountsById: Map<string, Account>;
  categoriesById: Map<string, Category>;
  onEdit: (transaction: Transaction) => void;
}

const STATUS_BADGE: Record<Transaction["status"], string> = {
  PENDING: "bg-warning/15 text-warning",
  POSTED: "bg-muted text-muted-foreground",
  CANCELED: "bg-muted text-muted-foreground line-through",
};

export function TransactionRow({ transaction, accountsById, categoriesById, onEdit }: TransactionRowProps) {
  const [confirming, setConfirming] = useState(false);
  const cancelTransaction = useCancelTransaction();

  const account = accountsById.get(transaction.accountId);
  const category = transaction.categoryId ? categoriesById.get(transaction.categoryId) : undefined;
  const isTransfer = transaction.type === "TRANSFER";
  const isOutflow = transaction.type === "EXPENSE" || transaction.transferDirection === "OUT";
  const canceled = transaction.status === "CANCELED";
  const editable = !isTransfer && !canceled;
  const cancelable = !canceled;

  return (
    <tr className={cn("border-b border-border last:border-0", canceled && "opacity-60")}>
      <td className="py-3 pr-4 pl-4 whitespace-nowrap text-muted-foreground">{formatDate(transaction.transactionDate)}</td>
      <td className="py-3 pr-4">
        <div className={cn("font-medium", canceled && "line-through")}>{transaction.description}</div>
        <div className="mt-0.5 flex items-center gap-1.5 text-xs text-muted-foreground">
          {isTransfer ? (
            <span className="inline-flex items-center gap-1">
              {transaction.transferDirection === "OUT" ? (
                <ArrowUpRight className="size-3.5" />
              ) : (
                <ArrowDownLeft className="size-3.5" />
              )}
              Transferência ({transaction.transferDirection === "OUT" ? "saída" : "entrada"})
            </span>
          ) : category ? (
            <span className="inline-flex items-center gap-1.5">
              <CategoryIcon icon={category.icon} color={category.color} className="size-4" />
              {category.name}
            </span>
          ) : (
            "Sem categoria"
          )}
          {transaction.installmentNumber && (
            <span>
              · {transaction.installmentNumber}/{transaction.installmentTotal}
            </span>
          )}
        </div>
      </td>
      <td className="py-3 pr-4 text-muted-foreground">{account?.name ?? "—"}</td>
      <td className={cn("py-3 pr-4 text-right font-medium tabular-nums", isOutflow ? "text-destructive" : "text-success")}>
        {isOutflow ? "-" : "+"}
        {formatMoney(transaction.amount)}
      </td>
      <td className="py-3 pr-4">
        <span className={cn("rounded-full px-2 py-0.5 text-xs whitespace-nowrap", STATUS_BADGE[transaction.status])}>
          {TRANSACTION_STATUS_LABELS[transaction.status]}
        </span>
      </td>
      <td className="py-3 pr-4 text-right">
        <div className="flex justify-end gap-2">
          {editable && (
            <Button variant="outline" size="sm" onClick={() => onEdit(transaction)} disabled={confirming}>
              Editar
            </Button>
          )}
          {cancelable &&
            (confirming ? (
              <>
                {isTransfer && (
                  <span className="self-center text-xs text-muted-foreground">As duas pernas serão canceladas.</span>
                )}
                <Button
                  variant="destructive"
                  size="sm"
                  disabled={cancelTransaction.isPending}
                  onClick={() => cancelTransaction.mutate(transaction.id, { onSettled: () => setConfirming(false) })}
                >
                  Confirmar
                </Button>
                <Button variant="ghost" size="sm" onClick={() => setConfirming(false)}>
                  Cancelar
                </Button>
              </>
            ) : (
              <Button variant="outline" size="sm" onClick={() => setConfirming(true)}>
                {isTransfer ? "Cancelar transferência" : "Cancelar"}
              </Button>
            ))}
        </div>
      </td>
    </tr>
  );
}
