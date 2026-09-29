import type { Account } from "@/api/accounts";
import { ACCOUNT_TYPE_ICONS, colorForAccount } from "@/lib/account-icons";
import { cn } from "@/lib/utils";

interface AccountIconProps {
  account: Pick<Account, "id" | "type">;
  className?: string;
}

/** Badge redondo colorido por conta — mesmo padrão visual do CategoryIcon. */
export function AccountIcon({ account, className }: AccountIconProps) {
  const Icon = ACCOUNT_TYPE_ICONS[account.type];
  return (
    <span
      className={cn("inline-flex size-9 shrink-0 items-center justify-center rounded-full text-white", className)}
      style={{ backgroundColor: colorForAccount(account.id) }}
    >
      <Icon className="size-4" />
    </span>
  );
}
