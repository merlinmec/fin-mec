import { Link } from "react-router-dom";
import type { Bill } from "@/api/bills";
import { BILL_STATUS_LABELS } from "@/api/bills";
import { formatDate } from "@/lib/dates";
import { formatMoney } from "@/lib/money";
import { cn } from "@/lib/utils";

interface UpcomingBillsCardProps {
  bills: Bill[];
}

export function UpcomingBillsCard({ bills }: UpcomingBillsCardProps) {
  if (bills.length === 0) {
    return <p className="text-sm text-muted-foreground">Nenhum vencimento em aberto.</p>;
  }

  return (
    <ul className="space-y-2">
      {bills.map((bill) => (
        <li key={bill.id} className="flex items-center justify-between gap-3 text-sm">
          <div className="min-w-0">
            <div className="truncate font-medium">{bill.description}</div>
            <div
              className={cn(
                "text-xs text-muted-foreground",
                bill.status === "OVERDUE" && "font-medium text-destructive",
              )}
            >
              {formatDate(bill.dueDate)} · {BILL_STATUS_LABELS[bill.status]}
            </div>
          </div>
          <span className="shrink-0 tabular-nums font-medium">{formatMoney(bill.amount)}</span>
        </li>
      ))}
      <li>
        <Link to="/contas-a-pagar" className="text-sm font-medium text-primary hover:underline">
          Ver todas as contas a pagar
        </Link>
      </li>
    </ul>
  );
}
