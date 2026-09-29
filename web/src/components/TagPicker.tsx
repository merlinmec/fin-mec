import { useState } from "react";
import { Check, Plus, Tags, X } from "lucide-react";
import type { Tag } from "@/api/tags";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { useSaveTag, useTags } from "@/hooks/useFeatureData";
import { cn } from "@/lib/utils";

const MAX_TAGS = 10;
const FALLBACK_COLOR = "var(--color-muted-foreground)";

export function TagChip({
  tag,
  onRemove,
  className,
}: {
  tag: Pick<Tag, "name" | "color">;
  onRemove?: () => void;
  className?: string;
}) {
  return (
    <span
      className={cn(
        "inline-flex max-w-40 items-center gap-1 rounded-full border border-border bg-surface-2 py-0.5 pr-1.5 pl-2 text-[11px] font-medium text-foreground/80",
        className,
      )}
    >
      <span
        className="size-1.5 shrink-0 rounded-full"
        style={{ backgroundColor: tag.color ?? FALLBACK_COLOR }}
      />
      <span className="truncate">{tag.name}</span>
      {onRemove && (
        <button
          type="button"
          onClick={onRemove}
          aria-label={`Remover tag ${tag.name}`}
          className="rounded-full hover:text-destructive"
        >
          <X className="size-3" />
        </button>
      )}
    </span>
  );
}

interface TagPickerProps {
  value: string[];
  onChange: (tagIds: string[]) => void;
  id?: string;
}

/**
 * Multisseleção de tags com busca e criação na hora ("Criar #viagem") — criar
 * uma tag não pode exigir sair do formulário de lançamento.
 */
export function TagPicker({ value, onChange, id }: TagPickerProps) {
  const { data: tags } = useTags();
  const saveTag = useSaveTag();
  const [query, setQuery] = useState("");
  const selected = (tags ?? []).filter((t) => value.includes(t.id));
  const normalized = query.trim().toLowerCase();
  const filtered = (tags ?? []).filter((t) => t.name.toLowerCase().includes(normalized));
  const exactExists = (tags ?? []).some((t) => t.name.toLowerCase() === normalized);
  const full = value.length >= MAX_TAGS;

  function toggle(tagId: string) {
    onChange(
      value.includes(tagId) ? value.filter((v) => v !== tagId) : full ? value : [...value, tagId],
    );
  }

  async function createFromQuery() {
    const created = await saveTag.mutateAsync({ payload: { name: query.trim() } });
    setQuery("");
    if (!full) onChange([...value, created.id]);
  }

  return (
    <div className="flex flex-wrap items-center gap-1.5">
      {selected.map((tag) => (
        <TagChip key={tag.id} tag={tag} onRemove={() => toggle(tag.id)} />
      ))}
      <Popover>
        <PopoverTrigger asChild>
          <button
            id={id}
            type="button"
            className="inline-flex h-7 items-center gap-1 rounded-full border border-dashed border-input px-2.5 text-xs font-medium text-muted-foreground transition-colors hover:border-primary hover:text-primary"
          >
            <Tags className="size-3.5" />
            {selected.length === 0 ? "Adicionar tags" : "Tags"}
          </button>
        </PopoverTrigger>
        <PopoverContent align="start" className="w-64 p-2">
          <input
            autoFocus
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter" && normalized && !exactExists) {
                e.preventDefault();
                void createFromQuery();
              }
            }}
            placeholder="Buscar ou criar tag…"
            maxLength={50}
            className="mb-1.5 h-8 w-full rounded-md border border-input bg-background px-2.5 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring"
          />
          <ul className="max-h-56 overflow-y-auto" role="listbox" aria-multiselectable>
            {filtered.map((tag) => {
              const checked = value.includes(tag.id);
              return (
                <li key={tag.id}>
                  <button
                    type="button"
                    role="option"
                    aria-selected={checked}
                    onClick={() => toggle(tag.id)}
                    disabled={!checked && full}
                    className="flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-left text-sm hover:bg-accent disabled:opacity-50"
                  >
                    <span
                      className="size-2 rounded-full"
                      style={{ backgroundColor: tag.color ?? FALLBACK_COLOR }}
                    />
                    <span className="flex-1 truncate">{tag.name}</span>
                    {checked && <Check className="size-4 text-primary" />}
                  </button>
                </li>
              );
            })}
            {normalized && !exactExists && (
              <li>
                <button
                  type="button"
                  onClick={() => void createFromQuery()}
                  disabled={saveTag.isPending}
                  className="flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-left text-sm text-primary hover:bg-accent"
                >
                  <Plus className="size-4" />
                  Criar “{query.trim()}”
                </button>
              </li>
            )}
            {!normalized && filtered.length === 0 && (
              <li className="px-2 py-3 text-center text-xs text-muted-foreground">
                Digite para criar a primeira tag.
              </li>
            )}
          </ul>
          {full && (
            <p className="px-2 pt-1 text-[11px] text-muted-foreground">
              Máximo de {MAX_TAGS} tags por lançamento.
            </p>
          )}
        </PopoverContent>
      </Popover>
    </div>
  );
}
