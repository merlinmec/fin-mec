import { useMemo, useState } from "react";
import type { CreditCard } from "@/api/creditCards";
import { useAccounts } from "@/hooks/useAccounts";
import { Button } from "@/components/ui/button";
import { useCreditCards } from "./hooks";
import { CreditCardRow } from "./CreditCardRow";
import { CreditCardFormDialog } from "./CreditCardFormDialog";

export function CreditCardsPage() {
  const { data: cards, isPending, isError } = useCreditCards();
  const { data: accounts } = useAccounts();
  const accountsById = useMemo(() => new Map((accounts ?? []).map((a) => [a.id, a])), [accounts]);

  const [formOpen, setFormOpen] = useState(false);
  const [editingCard, setEditingCard] = useState<CreditCard | undefined>(undefined);

  function openCreate() {
    setEditingCard(undefined);
    setFormOpen(true);
  }

  function openEdit(card: CreditCard) {
    setEditingCard(card);
    setFormOpen(true);
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold tracking-tight">Cartões</h1>
        <Button onClick={openCreate}>Novo cartão</Button>
      </div>

      {isPending && <p className="text-sm text-muted-foreground">Carregando cartões…</p>}

      {isError && (
        <p className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
          Não foi possível carregar os cartões. Tente recarregar a página.
        </p>
      )}

      {cards && cards.length === 0 && (
        <div className="rounded-xl border border-dashed border-border p-8 text-center text-sm text-muted-foreground">
          Nenhum cartão ainda. Crie o primeiro pra começar a lançar as compras.
        </div>
      )}

      {cards && cards.length > 0 && (
        <div className="overflow-x-auto rounded-xl border border-border">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-border text-left text-muted-foreground">
                <th className="py-2 pr-4 pl-4 font-medium">Nome</th>
                <th className="py-2 pr-4 text-right font-medium">Limite</th>
                <th className="py-2 pr-4 font-medium">Fechamento/Vencimento</th>
                <th className="py-2 pr-4 font-medium">Conta de pagamento</th>
                <th className="py-2 pr-4 font-medium" />
              </tr>
            </thead>
            <tbody>
              {cards.map((card) => (
                <CreditCardRow
                  key={card.id}
                  card={card}
                  paymentAccount={card.paymentAccountId ? accountsById.get(card.paymentAccountId) : undefined}
                  onEdit={openEdit}
                />
              ))}
            </tbody>
          </table>
        </div>
      )}

      <CreditCardFormDialog open={formOpen} onOpenChange={setFormOpen} card={editingCard} />
    </div>
  );
}
