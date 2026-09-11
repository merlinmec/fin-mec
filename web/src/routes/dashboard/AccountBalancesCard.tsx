import type { AccountBalance } from "@/api/dashboard";
import { ACCOUNT_TYPE_LABELS } from "@/api/accounts";
import { formatMoney } from "@/lib/money";
import { cn } from "@/lib/utils";

interface AccountBalancesCardProps {
  balances: AccountBalance[];
}

/**
 * Contabil (todos os POSTED, passados e futuros) x disponivel (POSTED so ate
 * hoje) — mesma distincao do backend, a UI so exibe as duas colunas lado a
 * lado por conta.
 */
export function AccountBalancesCard({ balances }: AccountBalancesCardProps) {
  if (balances.length === 0) {
    return <p className="text-sm text-muted-foreground">Nenhuma conta cadastrada ainda.</p>;
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="text-left text-xs text-muted-foreground">
            <th className="pb-2 font-medium">Conta</th>
            <th className="pb-2 text-right font-medium">Disponível</th>
            <th className="pb-2 text-right font-medium">Contábil</th>
          </tr>
        </thead>
        <tbody>
          {balances.map((balance) => (
            <tr key={balance.accountId} className="border-t border-border">
              <td className="py-2">
                <div className="font-medium">{balance.accountName}</div>
                <div className="text-xs text-muted-foreground">{ACCOUNT_TYPE_LABELS[balance.accountType]}</div>
              </td>
              <td
                className={cn(
                  "py-2 text-right tabular-nums",
                  balance.availableBalance < 0 && "text-destructive",
                )}
              >
                {formatMoney(balance.availableBalance)}
              </td>
              <td className="py-2 text-right tabular-nums text-muted-foreground">{formatMoney(balance.ledgerBalance)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
