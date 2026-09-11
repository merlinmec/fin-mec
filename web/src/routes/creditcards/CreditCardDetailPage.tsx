import { useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { ArrowLeft } from "lucide-react";
import type { CreditCardInvoiceStatus } from "@/api/creditCards";
import { Button } from "@/components/ui/button";
import { formatMoney } from "@/lib/money";
import { formatDate, formatYearMonth } from "@/lib/dates";
import { cn } from "@/lib/utils";
import { useCreditCards, useInvoices } from "./hooks";
import { ChargeFormDialog } from "./ChargeFormDialog";

const STATUS_BADGE: Record<CreditCardInvoiceStatus, string> = {
  OPEN: "bg-muted text-muted-foreground",
  CLOSED: "bg-warning/15 text-warning",
  PAID: "bg-success/15 text-success",
};

const STATUS_LABEL: Record<CreditCardInvoiceStatus, string> = {
  OPEN: "Aberta",
  CLOSED: "Fechada",
  PAID: "Paga",
};

export function CreditCardDetailPage() {
  const { cardId } = useParams<{ cardId: string }>();
  const navigate = useNavigate();
  const [chargeOpen, setChargeOpen] = useState(false);

  const { data: cards } = useCreditCards();
  const card = cards?.find((c) => c.id === cardId);
  const { data: invoices, isPending, isError } = useInvoices(cardId ?? "");

  if (!cardId) {
    return null;
  }

  return (
    <div className="space-y-6">
      <div>
        <Link to="/cartoes" className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
          <ArrowLeft className="size-4" />
          Cartões
        </Link>
      </div>

      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold tracking-tight">{card?.name ?? "Cartão"}</h1>
          {card && (
            <p className="text-sm text-muted-foreground">
              Limite {formatMoney(card.creditLimit)} · fecha dia {card.closingDay}, vence dia {card.dueDay}
            </p>
          )}
        </div>
        <Button onClick={() => setChargeOpen(true)}>Lançar compra</Button>
      </div>

      {isPending && <p className="text-sm text-muted-foreground">Carregando faturas…</p>}

      {isError && (
        <p className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
          Não foi possível carregar as faturas. Tente recarregar a página.
        </p>
      )}

      {invoices && invoices.length === 0 && (
        <div className="rounded-xl border border-dashed border-border p-8 text-center text-sm text-muted-foreground">
          Nenhuma fatura ainda — lance a primeira compra pra gerar uma.
        </div>
      )}

      {invoices && invoices.length > 0 && (
        <div className="overflow-x-auto rounded-xl border border-border">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-border text-left text-muted-foreground">
                <th className="py-2 pr-4 pl-4 font-medium">Competência</th>
                <th className="py-2 pr-4 font-medium">Fechamento</th>
                <th className="py-2 pr-4 font-medium">Vencimento</th>
                <th className="py-2 pr-4 text-right font-medium">Total</th>
                <th className="py-2 pr-4 font-medium">Status</th>
              </tr>
            </thead>
            <tbody>
              {invoices.map((invoice) => (
                <tr
                  key={invoice.id}
                  className="cursor-pointer border-b border-border last:border-0 hover:bg-accent/50"
                  onClick={() => void navigate(`/cartoes/${cardId}/faturas/${invoice.id}`)}
                >
                  <td className="py-3 pr-4 pl-4 font-medium">{formatYearMonth(invoice.referenceMonth)}</td>
                  <td className="py-3 pr-4 text-muted-foreground">{formatDate(invoice.closingDate)}</td>
                  <td className="py-3 pr-4 text-muted-foreground">{formatDate(invoice.dueDate)}</td>
                  <td
                    className={cn(
                      "py-3 pr-4 text-right font-medium tabular-nums",
                      invoice.totalAmount < 0 && "text-success",
                    )}
                  >
                    {formatMoney(invoice.totalAmount)}
                  </td>
                  <td className="py-3 pr-4">
                    <span className={cn("rounded-full px-2 py-0.5 text-xs whitespace-nowrap", STATUS_BADGE[invoice.status])}>
                      {STATUS_LABEL[invoice.status]}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <ChargeFormDialog open={chargeOpen} onOpenChange={setChargeOpen} cardId={cardId} />
    </div>
  );
}
