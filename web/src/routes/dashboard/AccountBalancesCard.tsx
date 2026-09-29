import type { AccountBalance } from "@/api/dashboard";
import { ACCOUNT_TYPE_LABELS } from "@/api/accounts";
import { AccountIcon } from "@/components/AccountIcon";
import { formatMoney } from "@/lib/money";
import { cn } from "@/lib/utils";

interface AccountBalancesCardProps {
  balances: AccountBalance[];
  hidden?: boolean;
}

const MASK = "••••••";

/**
 * Lista estilo "Minhas contas" do Organizze: ícone circular colorido + nome
 * + saldo disponível em destaque à direita. Contábil (todos os POSTED,
 * passados e futuros) vai como legenda menor — mesma distinção do backend,
 * só muda a apresentação (era tabela, agora lista).
 */
export function AccountBalancesCard({ balances, hidden = false }: AccountBalancesCardProps) {
  if (balances.length === 0) {
    return <p className="text-sm text-muted-foreground">Nenhuma conta cadastrada ainda.</p>;
  }

  return (
    <ul className="divide-y divide-border/60">
      {balances.map((balance) => (
        <li key={balance.accountId} className="flex items-center gap-3 py-2.5 first:pt-0 last:pb-0">
          <AccountIcon account={{ id: balance.accountId, type: balance.accountType }} />
          <div className="min-w-0 flex-1">
            <div className="truncate font-medium">{balance.accountName}</div>
            <div className="text-xs text-muted-foreground">{ACCOUNT_TYPE_LABELS[balance.accountType]}</div>
          </div>
          <div className="text-right">
            <div
              className={cn(
                "font-semibold tabular-nums",
                !hidden && balance.availableBalance < 0 && "text-destructive",
              )}
            >
              {hidden ? MASK : formatMoney(balance.availableBalance)}
            </div>
            <div className="text-xs text-muted-foreground tabular-nums">
              contábil: {hidden ? MASK : formatMoney(balance.ledgerBalance)}
            </div>
          </div>
        </li>
      ))}
    </ul>
  );
}
