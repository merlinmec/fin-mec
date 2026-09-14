import { useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { ArrowLeft, Receipt } from "lucide-react";
import type { Category } from "@/api/categories";
import type { CreditCardCharge } from "@/api/creditCards";
import { useCategories } from "@/hooks/useCategories";
import { CategoryIcon } from "@/components/CategoryIcon";
import { EmptyState } from "@/components/EmptyState";
import { TableSkeleton } from "@/components/TableSkeleton";
import { Button } from "@/components/ui/button";
import { formatDate, formatYearMonth } from "@/lib/dates";
import { formatMoney } from "@/lib/money";
import { cn } from "@/lib/utils";
import { useCreditCards, useDeleteCharge, useInvoice } from "./hooks";
import { PayInvoiceDialog } from "./PayInvoiceDialog";

const STATUS_LABEL = { OPEN: "Aberta", CLOSED: "Fechada", PAID: "Paga" } as const;

export function InvoiceDetailPage() {
  const { cardId, invoiceId } = useParams<{ cardId: string; invoiceId: string }>();
  const [payOpen, setPayOpen] = useState(false);

  const { data: invoice, isPending, isError } = useInvoice(invoiceId ?? "");
  const { data: cards } = useCreditCards();
  const { data: categories } = useCategories();
  const categoriesById = useMemo(() => new Map((categories ?? []).map((c) => [c.id, c])), [categories]);
  const card = cards?.find((c) => c.id === cardId);

  if (!cardId || !invoiceId) {
    return null;
  }

  const canManage = invoice?.status === "OPEN";

  return (
    <div className="space-y-6">
      <div>
        <Link to={`/cartoes/${cardId}`} className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
          <ArrowLeft className="size-4" />
          {card?.name ?? "Cartão"}
        </Link>
      </div>

      {isPending && <TableSkeleton columns={3} />}

      {isError && (
        <p className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
          Não foi possível carregar a fatura. Tente recarregar a página.
        </p>
      )}

      {invoice && (
        <>
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <h1 className="text-xl font-semibold tracking-tight">Fatura {formatYearMonth(invoice.referenceMonth)}</h1>
              <p className="text-sm text-muted-foreground">
                Fecha em {formatDate(invoice.closingDate)}, vence em {formatDate(invoice.dueDate)} · {STATUS_LABEL[invoice.status]} ·
                total {formatMoney(invoice.totalAmount)}
              </p>
            </div>
            {invoice.status === "OPEN" && <Button onClick={() => setPayOpen(true)}>Pagar fatura</Button>}
          </div>

          {!canManage && (
            <p className="text-xs text-muted-foreground">
              {invoice.status === "PAID"
                ? "Fatura já paga — não é mais possível editar as cobranças."
                : "Fatura fechada — não é mais possível editar as cobranças por aqui."}
            </p>
          )}

          {invoice.charges.length === 0 ? (
            <EmptyState icon={Receipt} title="Nenhuma cobrança nessa fatura" />
          ) : (
            <div className="overflow-x-auto rounded-xl border border-border">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-border text-left text-muted-foreground">
                    <th className="py-2 pr-4 pl-4 font-medium">Data</th>
                    <th className="py-2 pr-4 font-medium">Descrição</th>
                    <th className="py-2 pr-4 text-right font-medium">Valor</th>
                    {canManage && <th className="py-2 pr-4 font-medium" />}
                  </tr>
                </thead>
                <tbody>
                  {invoice.charges.map((charge) => (
                    <ChargeRow
                      key={charge.id}
                      charge={charge}
                      category={charge.categoryId ? categoriesById.get(charge.categoryId) : undefined}
                      cardId={cardId}
                      invoiceId={invoiceId}
                      canManage={canManage}
                    />
                  ))}
                </tbody>
              </table>
            </div>
          )}

          <PayInvoiceDialog
            open={payOpen}
            onOpenChange={setPayOpen}
            invoice={invoice}
            cardId={cardId}
            defaultAccountId={card?.paymentAccountId ?? undefined}
          />
        </>
      )}
    </div>
  );
}

function ChargeRow({
  charge,
  category,
  cardId,
  invoiceId,
  canManage,
}: {
  charge: CreditCardCharge;
  category: Category | undefined;
  cardId: string;
  invoiceId: string;
  canManage: boolean;
}) {
  const [confirming, setConfirming] = useState(false);
  const deleteCharge = useDeleteCharge(cardId, invoiceId);
  const isRefund = charge.type === "INCOME";

  return (
    <tr className="border-b border-border last:border-0">
      <td className="py-3 pr-4 pl-4 text-muted-foreground whitespace-nowrap">{formatDate(charge.purchaseDate)}</td>
      <td className="py-3 pr-4">
        <div className="font-medium">{charge.description}</div>
        <div className="mt-0.5 flex items-center gap-1.5 text-xs text-muted-foreground">
          {category && (
            <span className="inline-flex items-center gap-1.5">
              <CategoryIcon icon={category.icon} color={category.color} className="size-4" />
              {category.name}
            </span>
          )}
          {isRefund && <span className="text-success">Estorno</span>}
          {charge.installmentNumber && (
            <span>
              · {charge.installmentNumber}/{charge.installmentTotal}
            </span>
          )}
        </div>
      </td>
      <td className={cn("py-3 pr-4 text-right font-medium tabular-nums", isRefund ? "text-success" : "")}>
        {isRefund ? "-" : ""}
        {formatMoney(charge.amount)}
      </td>
      {canManage && (
        <td className="py-3 pr-4 text-right">
          {confirming ? (
            <div className="flex justify-end gap-2">
              <Button
                variant="destructive"
                size="sm"
                disabled={deleteCharge.isPending}
                onClick={() => deleteCharge.mutate(charge.id, { onSettled: () => setConfirming(false) })}
              >
                Confirmar
              </Button>
              <Button variant="ghost" size="sm" onClick={() => setConfirming(false)}>
                Cancelar
              </Button>
            </div>
          ) : (
            <div className="flex justify-end">
              <Button variant="outline" size="sm" onClick={() => setConfirming(true)}>
                Remover
              </Button>
            </div>
          )}
        </td>
      )}
    </tr>
  );
}
