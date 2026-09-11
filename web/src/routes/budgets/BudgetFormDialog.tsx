import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import type { Budget } from "@/api/budgets";
import type { Category } from "@/api/categories";
import { formatYearMonth } from "@/lib/dates";
import { getErrorMessage } from "@/lib/errors";
import { CategoryIcon } from "@/components/CategoryIcon";
import { CategorySelect } from "@/components/CategorySelect";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { useCreateBudget, useUpdateBudget } from "./hooks";
import { createBudgetSchema, updateBudgetSchema, type CreateBudgetFormValues, type UpdateBudgetFormValues } from "./budget-schema";

interface BudgetFormDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Presente (com a categoria ja resolvida, so pra exibicao) = editar; ausente = criar. */
  budget?: Budget;
  category?: Category;
  /** Mes pre-selecionado ao criar — o mesmo que ja esta filtrado na pagina. */
  defaultMonth: string;
}

export function BudgetFormDialog({ open, onOpenChange, budget, category, defaultMonth }: BudgetFormDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        {budget ? (
          <EditBudgetForm key={budget.id} budget={budget} category={category} onDone={() => onOpenChange(false)} />
        ) : (
          <CreateBudgetForm key="new" defaultMonth={defaultMonth} onDone={() => onOpenChange(false)} />
        )}
      </DialogContent>
    </Dialog>
  );
}

function CreateBudgetForm({ defaultMonth, onDone }: { defaultMonth: string; onDone: () => void }) {
  const createBudget = useCreateBudget();
  const [formError, setFormError] = useState<string | null>(null);

  const {
    handleSubmit,
    watch,
    setValue,
    register,
    formState: { errors, isSubmitting },
  } = useForm<CreateBudgetFormValues>({
    resolver: zodResolver(createBudgetSchema),
    defaultValues: { categoryId: "", referenceMonth: defaultMonth, amount: 0 },
  });

  const categoryId = watch("categoryId");

  async function onSubmit(values: CreateBudgetFormValues) {
    setFormError(null);
    try {
      await createBudget.mutateAsync(values);
      onDone();
    } catch (err) {
      setFormError(getErrorMessage(err, "Não foi possível criar o orçamento."));
    }
  }

  return (
    <>
      <DialogHeader>
        <DialogTitle>Novo orçamento</DialogTitle>
      </DialogHeader>

      <form className="space-y-4" onSubmit={(e) => void handleSubmit(onSubmit)(e)} noValidate>
        <div className="space-y-1.5">
          <Label htmlFor="budget-category">Categoria</Label>
          <CategorySelect
            id="budget-category"
            type="EXPENSE"
            value={categoryId}
            onValueChange={(v) => setValue("categoryId", v ?? "", { shouldValidate: true })}
          />
          {errors.categoryId && <p className="text-sm text-destructive">{errors.categoryId.message}</p>}
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div className="space-y-1.5">
            <Label htmlFor="budget-month">Mês</Label>
            <Input id="budget-month" type="month" aria-invalid={!!errors.referenceMonth} {...register("referenceMonth")} />
            {errors.referenceMonth && <p className="text-sm text-destructive">{errors.referenceMonth.message}</p>}
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="budget-amount">Limite planejado</Label>
            <Input
              id="budget-amount"
              type="number"
              step="0.01"
              aria-invalid={!!errors.amount}
              {...register("amount", { valueAsNumber: true })}
            />
            {errors.amount && <p className="text-sm text-destructive">{errors.amount.message}</p>}
          </div>
        </div>

        {formError && (
          <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
            {formError}
          </p>
        )}

        <DialogFooter>
          <Button type="submit" disabled={isSubmitting}>
            {isSubmitting ? "Criando…" : "Criar orçamento"}
          </Button>
        </DialogFooter>
      </form>
    </>
  );
}

function EditBudgetForm({ budget, category, onDone }: { budget: Budget; category?: Category; onDone: () => void }) {
  const updateBudget = useUpdateBudget();
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<UpdateBudgetFormValues>({
    resolver: zodResolver(updateBudgetSchema),
    defaultValues: { amount: budget.amount },
  });

  async function onSubmit(values: UpdateBudgetFormValues) {
    setFormError(null);
    try {
      await updateBudget.mutateAsync({ id: budget.id, payload: values });
      onDone();
    } catch (err) {
      setFormError(getErrorMessage(err, "Não foi possível atualizar o orçamento."));
    }
  }

  return (
    <>
      <DialogHeader>
        <DialogTitle>Editar orçamento</DialogTitle>
      </DialogHeader>

      <form className="space-y-4" onSubmit={(e) => void handleSubmit(onSubmit)(e)} noValidate>
        <div className="flex items-center gap-2 rounded-md border border-border bg-muted/40 px-3 py-2 text-sm">
          {category && <CategoryIcon icon={category.icon} color={category.color} />}
          <span className="font-medium">{category?.name ?? "Categoria"}</span>
          <span className="text-muted-foreground">· {formatYearMonth(budget.referenceMonth)}</span>
        </div>
        <p className="text-xs text-muted-foreground">Categoria e mês não são editáveis — exclua e crie outro orçamento se precisar mudar.</p>

        <div className="space-y-1.5">
          <Label htmlFor="edit-budget-amount">Limite planejado</Label>
          <Input
            id="edit-budget-amount"
            type="number"
            step="0.01"
            aria-invalid={!!errors.amount}
            {...register("amount", { valueAsNumber: true })}
          />
          {errors.amount && <p className="text-sm text-destructive">{errors.amount.message}</p>}
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
