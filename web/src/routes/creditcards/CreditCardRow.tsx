import { useState } from "react";
import { useNavigate } from "react-router-dom";
import type { Account } from "@/api/accounts";
import type { CreditCard } from "@/api/creditCards";
import { Button } from "@/components/ui/button";
import { formatMoney } from "@/lib/money";
import { useDeleteCreditCard } from "./hooks";

interface CreditCardRowProps {
  card: CreditCard;
  paymentAccount: Account | undefined;
  onEdit: (card: CreditCard) => void;
}

/** Mesmo padrao de confirmacao em duas etapas do AccountRow (FE-2). */
export function CreditCardRow({ card, paymentAccount, onEdit }: CreditCardRowProps) {
  const [confirming, setConfirming] = useState(false);
  const deleteCard = useDeleteCreditCard();
  const navigate = useNavigate();

  return (
    <tr className="border-b border-border last:border-0">
      <td className="py-3 pr-4 pl-4">
        <div className="font-medium">{card.name}</div>
        {card.archived && (
          <span className="mt-0.5 inline-block rounded-full bg-muted px-2 py-0.5 text-xs text-muted-foreground">
            Arquivado
          </span>
        )}
      </td>
      <td className="py-3 pr-4 text-right tabular-nums">{formatMoney(card.creditLimit)}</td>
      <td className="py-3 pr-4 text-muted-foreground whitespace-nowrap">
        Fecha dia {card.closingDay}, vence dia {card.dueDay}
      </td>
      <td className="py-3 pr-4 text-muted-foreground">{paymentAccount?.name ?? "—"}</td>
      <td className="py-3 pr-4 text-right">
        <div className="flex justify-end gap-2">
          <Button size="sm" onClick={() => void navigate(`/cartoes/${card.id}`)} disabled={confirming}>
            Ver faturas
          </Button>
          <Button variant="outline" size="sm" onClick={() => onEdit(card)} disabled={confirming}>
            Editar
          </Button>
          {confirming ? (
            <>
              <Button
                variant="destructive"
                size="sm"
                disabled={deleteCard.isPending}
                onClick={() => deleteCard.mutate(card.id, { onSettled: () => setConfirming(false) })}
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
