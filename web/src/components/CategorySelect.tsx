import { CATEGORY_TYPE_LABELS, type CategoryType } from "@/api/categories";
import { useCategories } from "@/hooks/useCategories";
import { CategoryIcon } from "@/components/CategoryIcon";
import { Select, SelectContent, SelectGroup, SelectItem, SelectLabel, SelectTrigger, SelectValue } from "@/components/ui/select";

interface CategorySelectProps {
  id?: string;
  value: string | undefined;
  onValueChange: (id: string) => void;
  /** Restringe as opcoes a um tipo (ex.: formulario de despesa so mostra categorias EXPENSE). Sem isso, agrupa os dois tipos. */
  type?: CategoryType;
  disabled?: boolean;
  placeholder?: string;
}

/**
 * Picker reutilizavel de categoria (icone + cor + nome), introduzido na FE-3
 * antes dos consumidores (lancamento, orcamento, conta a pagar, cartao —
 * todos referenciam categoryId). Radix Select por baixo (ver
 * components/ui/select.tsx) porque uma option nativa nao renderiza
 * icone/swatch de cor.
 */
export function CategorySelect({ id, value, onValueChange, type, disabled, placeholder = "Selecione uma categoria" }: CategorySelectProps) {
  const { data: categories } = useCategories();
  const filtered = (categories ?? []).filter((c) => !type || c.type === type);

  const groups = type
    ? [{ type, items: filtered }]
    : (["EXPENSE", "INCOME"] as const)
        .map((t) => ({ type: t, items: filtered.filter((c) => c.type === t) }))
        .filter((g) => g.items.length > 0);

  return (
    <Select value={value} onValueChange={onValueChange} disabled={disabled}>
      <SelectTrigger id={id}>
        <SelectValue placeholder={placeholder} />
      </SelectTrigger>
      <SelectContent>
        {groups.map((group) => (
          <SelectGroup key={group.type}>
            {!type && <SelectLabel>{CATEGORY_TYPE_LABELS[group.type]}</SelectLabel>}
            {group.items.map((category) => (
              <SelectItem key={category.id} value={category.id}>
                <span className="flex items-center gap-2">
                  <CategoryIcon icon={category.icon} color={category.color} />
                  {category.name}
                </span>
              </SelectItem>
            ))}
          </SelectGroup>
        ))}
      </SelectContent>
    </Select>
  );
}
