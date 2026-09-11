import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import type { Category } from "@/api/categories";
import { CATEGORY_TYPES, CATEGORY_TYPE_LABELS } from "@/api/categories";
import { CATEGORY_COLORS, CATEGORY_ICONS, CATEGORY_ICON_NAMES } from "@/lib/category-icons";
import { getErrorMessage } from "@/lib/errors";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect } from "@/components/ui/native-select";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { useCreateCategory, useUpdateCategory } from "./hooks";
import { categoryFormSchema, type CategoryFormValues } from "./category-schema";

interface CategoryFormDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Presente = editar essa categoria; ausente = criar uma nova. */
  category?: Category;
}

export function CategoryFormDialog({ open, onOpenChange, category }: CategoryFormDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <CategoryForm key={category?.id ?? "new"} category={category} onDone={() => onOpenChange(false)} />
      </DialogContent>
    </Dialog>
  );
}

function CategoryForm({ category, onDone }: { category?: Category; onDone: () => void }) {
  const createCategory = useCreateCategory();
  const updateCategory = useUpdateCategory();
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<CategoryFormValues>({
    resolver: zodResolver(categoryFormSchema),
    defaultValues: {
      name: category?.name ?? "",
      type: category?.type ?? "EXPENSE",
      color: category?.color ?? CATEGORY_COLORS[0],
      icon: category?.icon ?? CATEGORY_ICON_NAMES[0],
    },
  });

  const selectedColor = watch("color");
  const selectedIcon = watch("icon");

  async function onSubmit(values: CategoryFormValues) {
    setFormError(null);
    try {
      if (category) {
        await updateCategory.mutateAsync({ id: category.id, payload: values });
      } else {
        await createCategory.mutateAsync(values);
      }
      onDone();
    } catch (err) {
      setFormError(getErrorMessage(err, "Não foi possível salvar a categoria."));
    }
  }

  return (
    <>
      <DialogHeader>
        <DialogTitle>{category ? "Editar categoria" : "Nova categoria"}</DialogTitle>
      </DialogHeader>

      <form className="space-y-4" onSubmit={(e) => void handleSubmit(onSubmit)(e)} noValidate>
        <div className="space-y-1.5">
          <Label htmlFor="cat-name">Nome</Label>
          <Input id="cat-name" autoComplete="off" aria-invalid={!!errors.name} {...register("name")} />
          {errors.name && <p className="text-sm text-destructive">{errors.name.message}</p>}
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="cat-type">Tipo</Label>
          <NativeSelect id="cat-type" aria-invalid={!!errors.type} {...register("type")}>
            {CATEGORY_TYPES.map((type) => (
              <option key={type} value={type}>
                {CATEGORY_TYPE_LABELS[type]}
              </option>
            ))}
          </NativeSelect>
          {errors.type && <p className="text-sm text-destructive">{errors.type.message}</p>}
        </div>

        <div className="space-y-1.5">
          <Label>Cor</Label>
          <div className="flex flex-wrap gap-2">
            {CATEGORY_COLORS.map((color) => (
              <button
                key={color}
                type="button"
                aria-label={color}
                aria-pressed={selectedColor === color}
                onClick={() => setValue("color", color, { shouldValidate: true })}
                className={cn(
                  "size-7 rounded-full border-2 transition-colors",
                  selectedColor === color ? "border-foreground" : "border-transparent",
                )}
                style={{ backgroundColor: color }}
              />
            ))}
          </div>
        </div>

        <div className="space-y-1.5">
          <Label>Ícone</Label>
          <div className="grid grid-cols-8 gap-1.5">
            {CATEGORY_ICON_NAMES.map((name) => {
              const Icon = CATEGORY_ICONS[name];
              const active = selectedIcon === name;
              return (
                <button
                  key={name}
                  type="button"
                  aria-label={name}
                  aria-pressed={active}
                  onClick={() => setValue("icon", name, { shouldValidate: true })}
                  className={cn(
                    "flex size-8 items-center justify-center rounded-md border transition-colors",
                    active
                      ? "border-primary bg-primary/10 text-primary"
                      : "border-transparent text-muted-foreground hover:bg-accent",
                  )}
                >
                  <Icon className="size-4" />
                </button>
              );
            })}
          </div>
        </div>

        {formError && (
          <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
            {formError}
          </p>
        )}

        <DialogFooter>
          <Button type="submit" disabled={isSubmitting}>
            {isSubmitting ? "Salvando…" : "Salvar"}
          </Button>
        </DialogFooter>
      </form>
    </>
  );
}
