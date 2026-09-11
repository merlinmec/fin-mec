import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import type { Bill } from "@/api/bills";
import type { RecurrenceRule } from "@/api/transactions";
import { RECURRENCE_RULES, RECURRENCE_RULE_LABELS } from "@/api/transactions";
import { todayIso } from "@/lib/dates";
import { getErrorMessage } from "@/lib/errors";
import { AccountSelect } from "@/components/AccountSelect";
import { CategorySelect } from "@/components/CategorySelect";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect } from "@/components/ui/native-select";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { useCreateBill, useUpdateBill } from "./hooks";
import { billSchema, type BillFormValues } from "./bill-schema";

interface BillFormDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Presente = editar essa conta a pagar; ausente = criar uma nova. Only OPEN/OVERDUE sao editaveis (o backend recusa o resto, ver requireOpen). */
  bill?: Bill;
}

export function BillFormDialog({ open, onOpenChange, bill }: BillFormDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <BillForm key={bill?.id ?? "new"} bill={bill} onDone={() => onOpenChange(false)} />
      </DialogContent>
    </Dialog>
  );
}

function BillForm({ bill, onDone }: { bill?: Bill; onDone: () => void }) {
  const createBill = useCreateBill();
  const updateBill = useUpdateBill();
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<BillFormValues>({
    resolver: zodResolver(billSchema),
    defaultValues: bill
      ? {
          description: bill.description,
          amount: bill.amount,
          dueDate: bill.dueDate,
          sourceAccountId: bill.sourceAccountId ?? undefined,
          categoryId: bill.categoryId ?? undefined,
          recurrenceRule: bill.recurrenceRule ?? "",
        }
      : {
          description: "",
          amount: 0,
          dueDate: todayIso(),
          sourceAccountId: undefined,
          categoryId: undefined,
          recurrenceRule: "",
        },
  });

  const sourceAccountId = watch("sourceAccountId");
  const categoryId = watch("categoryId");

  async function onSubmit(values: BillFormValues) {
    setFormError(null);
    const payload = {
      description: values.description,
      amount: values.amount,
      dueDate: values.dueDate,
      sourceAccountId: values.sourceAccountId,
      categoryId: values.categoryId,
      recurrenceRule: values.recurrenceRule ? (values.recurrenceRule as RecurrenceRule) : undefined,
    };
    try {
      if (bill) {
        await updateBill.mutateAsync({ id: bill.id, payload });
      } else {
        await createBill.mutateAsync(payload);
      }
      onDone();
    } catch (err) {
      setFormError(getErrorMessage(err, "Não foi possível salvar a conta a pagar."));
    }
  }

  return (
    <>
      <DialogHeader>
        <DialogTitle>{bill ? "Editar conta a pagar" : "Nova conta a pagar"}</DialogTitle>
      </DialogHeader>

      <form className="space-y-4" onSubmit={(e) => void handleSubmit(onSubmit)(e)} noValidate>
        <div className="space-y-1.5">
          <Label htmlFor="bill-description">Descrição</Label>
          <Input id="bill-description" autoComplete="off" aria-invalid={!!errors.description} {...register("description")} />
          {errors.description && <p className="text-sm text-destructive">{errors.description.message}</p>}
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div className="space-y-1.5">
            <Label htmlFor="bill-amount">Valor</Label>
            <Input
              id="bill-amount"
              type="number"
              step="0.01"
              aria-invalid={!!errors.amount}
              {...register("amount", { valueAsNumber: true })}
            />
            {errors.amount && <p className="text-sm text-destructive">{errors.amount.message}</p>}
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="bill-due-date">Vencimento</Label>
            <Input id="bill-due-date" type="date" aria-invalid={!!errors.dueDate} {...register("dueDate")} />
            {errors.dueDate && <p className="text-sm text-destructive">{errors.dueDate.message}</p>}
          </div>
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="bill-account">Conta de origem (opcional)</Label>
          <AccountSelect
            id="bill-account"
            value={sourceAccountId}
            onValueChange={(v) => setValue("sourceAccountId", v)}
            clearable
            clearLabel="Sem conta padrão"
            placeholder="Sem conta padrão"
          />
          <p className="text-xs text-muted-foreground">Usada como sugestão ao dar baixa — pode ser trocada na hora do pagamento.</p>
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="bill-category">Categoria</Label>
          <CategorySelect id="bill-category" type="EXPENSE" value={categoryId} onValueChange={(v) => setValue("categoryId", v)} clearable />
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="bill-recurrence">Recorrência (informativo)</Label>
          <NativeSelect id="bill-recurrence" {...register("recurrenceRule")}>
            <option value="">Não recorrente</option>
            {RECURRENCE_RULES.map((r) => (
              <option key={r} value={r}>
                {RECURRENCE_RULE_LABELS[r]}
              </option>
            ))}
          </NativeSelect>
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
