import { CATEGORY_COLORS, CATEGORY_ICONS } from "@/lib/category-icons";
import { cn } from "@/lib/utils";

/** Paleta de cores com nome acessível (o hex sozinho não diz nada a um leitor de tela). */
const COLOR_NAMES: Record<string, string> = {
  "#8B5CF6": "Violeta",
  "#F59E0B": "Âmbar",
  "#3B82F6": "Azul",
  "#EF4444": "Vermelho",
  "#10B981": "Esmeralda",
  "#EC4899": "Rosa",
  "#6B7280": "Cinza",
  "#14B8A6": "Turquesa",
  "#F97316": "Laranja",
  "#84CC16": "Lima",
  "#06B6D4": "Ciano",
  "#A855F7": "Roxo",
};

export function ColorSwatchPicker({
  value,
  onChange,
}: {
  value: string | null | undefined;
  onChange: (color: string) => void;
}) {
  return (
    <div className="flex flex-wrap gap-2" role="radiogroup" aria-label="Cor">
      {CATEGORY_COLORS.map((color) => (
        <button
          key={color}
          type="button"
          role="radio"
          aria-label={COLOR_NAMES[color] ?? color}
          aria-checked={value === color}
          onClick={() => onChange(color)}
          className={cn(
            "size-7 rounded-full ring-offset-2 ring-offset-card transition-all",
            value === color ? "scale-110 ring-2 ring-foreground" : "hover:scale-105",
          )}
          style={{ backgroundColor: color }}
        />
      ))}
    </div>
  );
}

export function IconPicker({
  value,
  onChange,
  names,
}: {
  value: string | null | undefined;
  onChange: (icon: string) => void;
  /** Subconjunto de ícones a oferecer (padrão: todos os de categoria). */
  names?: string[];
}) {
  const options = names ?? Object.keys(CATEGORY_ICONS);
  return (
    <div className="flex flex-wrap gap-1.5" role="radiogroup" aria-label="Ícone">
      {options.map((name) => {
        const Icon = CATEGORY_ICONS[name];
        if (!Icon) return null;
        const active = value === name;
        return (
          <button
            key={name}
            type="button"
            role="radio"
            aria-label={name}
            aria-checked={active}
            onClick={() => onChange(name)}
            className={cn(
              "flex size-9 items-center justify-center rounded-lg border transition-colors",
              active
                ? "border-primary bg-primary/10 text-primary"
                : "border-border text-muted-foreground hover:bg-accent",
            )}
          >
            <Icon className="size-4" />
          </button>
        );
      })}
    </div>
  );
}
