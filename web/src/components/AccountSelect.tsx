import { ACCOUNT_TYPE_LABELS } from "@/api/accounts";
import { useAccounts } from "@/hooks/useAccounts";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";

// Mesmo motivo do CategorySelect: Radix Select nao aceita value="".
const NONE = "__none__";

interface AccountSelectProps {
  id?: string;
  value: string | undefined;
  onValueChange: (id: string | undefined) => void;
  disabled?: boolean;
  placeholder?: string;
  /** Filtro de listagem quer ver contas arquivadas tambem; lancar em conta arquivada nao faz sentido. */
  includeArchived?: boolean;
  clearable?: boolean;
  clearLabel?: string;
}

/** Picker reutilizavel de conta, usado pelos formularios de lancamento/transferencia/parcelamento (FE-4) e pelo filtro da lista. */
export function AccountSelect({
  id,
  value,
  onValueChange,
  disabled,
  placeholder = "Selecione uma conta",
  includeArchived = false,
  clearable = false,
  clearLabel = "Todas as contas",
}: AccountSelectProps) {
  const { data: accounts } = useAccounts();
  const options = (accounts ?? []).filter((a) => includeArchived || !a.archived);

  return (
    <Select
      value={value ?? NONE}
      onValueChange={(next) => onValueChange(next === NONE ? undefined : next)}
      disabled={disabled}
    >
      <SelectTrigger id={id}>
        <SelectValue placeholder={placeholder} />
      </SelectTrigger>
      <SelectContent>
        {clearable && <SelectItem value={NONE}>{clearLabel}</SelectItem>}
        {options.map((account) => (
          <SelectItem key={account.id} value={account.id}>
            {account.name}
            <span className="text-muted-foreground"> — {ACCOUNT_TYPE_LABELS[account.type]}</span>
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}
