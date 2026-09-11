import { useState } from "react";
import type { Bill } from "@/api/bills";
import { BILL_STATUS_LABELS } from "@/api/bills";
import type { Category } from "@/api/categories";
import { CategoryIcon } from "@/components/CategoryIcon";
import { Button } from "@/components/ui/button";
import { daysUntil, formatDate } from "@/lib/dates";
import { formatMoney } from "@/lib/money";
import { cn } from "@/lib/utils";
import { useCancelBill } from "./hooks";

// "A vencer" e um destaque de UX (nao existe no backend) — 7 dias e o valor
// que pareceu razoavel pra uma conta pessoal; sem indicacao no plano do
// numero exato.
const DUE_SOON_DAYS = 7;

interface BillRowProps {
  bill: Bill;
  category: Category | undefined;
  onEdit: (bill: Bill) => void;
  onPay: (bill: Bill) => void;
}

const STATUS_BADGE: Record<Bill["status"], string> = {
  OPEN: "bg-muted text-muted-foreground",
  OVERDUE: "bg-destructive/15 text-destructive",
  PAID: "bg-success/15 text-success",
  CANCELED: "bg-muted text-muted-foreground line-through",
};

export function BillRow({ bill, category, onEdit, onPay }: BillRowProps) {
  const [confirming, setConfirming] = useState(false);
  const cancelBill = useCancelBill();

  const canceled = bill.status === "CANCELED";
  const actionable = bill.status === "OPEN" || bill.status === "OVERDUE";
  const dueSoon = bill.status === "OPEN" && daysUntil(bill.dueDate) >= 0 && daysUntil(bill.dueDate) <= DUE_SOON_DAYS;

  return (
    <tr className={cn("border-b border-border last:border-0", canceled && "opacity-60")}>
      <td className="py-3 pr-4 pl-4">
        <div className={cn("font-medium", canceled && "line-through")}>{bill.description}</div>
        {category && (
          <div className="mt-0.5 flex items-center gap-1.5 text-xs text-muted-foreground">
            <CategoryIcon icon={category.icon} color={category.color} className="size-4" />
            {category.name}
          </div>
        )}
      </td>
      <td className={cn("py-3 pr-4 whitespace-nowrap", bill.status === "OVERDUE" && "font-medium text-destructive")}>
        {formatDate(bill.dueDate)}
        {dueSoon && <div className="text-xs text-warning">Vence em {daysUntil(bill.dueDate)} dia(s)</div>}
      </td>
      <td className="py-3 pr-4 text-right font-medium tabular-nums">{formatMoney(bill.amount)}</td>
      <td className="py-3 pr-4">
        <span className={cn("rounded-full px-2 py-0.5 text-xs whitespace-nowrap", STATUS_BADGE[bill.status])}>
          {BILL_STATUS_LABELS[bill.status]}
        </span>
      </td>
      <td className="py-3 pr-4 text-right">
        {actionable && (
          <div className="flex justify-end gap-2">
            <Button size="sm" onClick={() => onPay(bill)} disabled={confirming}>
              Dar baixa
            </Button>
            <Button variant="outline" size="sm" onClick={() => onEdit(bill)} disabled={confirming}>
              Editar
            </Button>
            {confirming ? (
              <>
                <Button
                  variant="destructive"
                  size="sm"
                  disabled={cancelBill.isPending}
                  onClick={() => cancelBill.mutate(bill.id, { onSettled: () => setConfirming(false) })}
                >
                  Confirmar
                </Button>
                <Button variant="ghost" size="sm" onClick={() => setConfirming(false)}>
                  Cancelar
                </Button>
              </>
            ) : (
              <Button variant="outline" size="sm" onClick={() => setConfirming(true)}>
                Cancelar
              </Button>
            )}
          </div>
        )}
      </td>
    </tr>
  );
}
