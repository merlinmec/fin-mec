import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import type { EntryType } from "@/api/transactions";
import { ENTRY_TYPES, TRANSACTION_TYPE_LABELS } from "@/api/transactions";
import { currentYearMonth, formatDate, formatYearMonth, shiftYearMonth, todayIso } from "@/lib/dates";
import { formatMoney } from "@/lib/money";
import { getErrorMessage } from "@/lib/errors";
import { AccountSelect } from "@/components/AccountSelect";
import { CategorySelect } from "@/components/CategorySelect";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect } from "@/components/ui/native-select";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { useCreateInstallments } from "./hooks";
import { installmentSchema, type InstallmentFormValues } from "./transaction-schema";

interface InstallmentFormDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

/** Parcelamento — materializa as N parcelas de uma vez; preview antes de confirmar (valor, datas, quantidade). */
export function InstallmentFormDialog({ open, onOpenChange }: InstallmentFormDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-lg">
        <InstallmentForm key={open ? "open" : "closed"} onDone={() => onOpenChange(false)} />
      </DialogContent>
    </Dialog>
  );
}

function addMonths(isoDate: string, months: number): string {
  const [y, m, d] = isoDate.split("-").map(Number);
  const date = new Date(y, m - 1 + months, d);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

function InstallmentForm({ onDone }: { onDone: () => void }) {
  const createInstallments = useCreateInstallments();
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<InstallmentFormValues>({
    resolver: zodResolver(installmentSchema),
    defaultValues: {
      accountId: "",
      categoryId: undefined,
      type: "EXPENSE",
      amountPerInstallment: 0,
      description: "",
      firstTransactionDate: todayIso(),
      firstCompetenceMonth: currentYearMonth(),
      installments: 2,
    },
  });

  const type = watch("type");
  const accountId = watch("accountId");
  const categoryId = watch("categoryId");
  const amountPerInstallment = watch("amountPerInstallment");
  const firstTransactionDate = watch("firstTransactionDate");
  const firstCompetenceMonth = watch("firstCompetenceMonth");
  const installments = watch("installments");

  const previewValid =
    Number.isFinite(amountPerInstallment) && amountPerInstallment > 0 && Number.isInteger(installments) && installments >= 2;
  const preview = previewValid
    ? Array.from({ length: installments }, (_, i) => ({
        number: i + 1,
        date: addMonths(firstTransactionDate, i),
        month: shiftYearMonth(firstCompetenceMonth, i),
      }))
    : [];

  async function onSubmit(values: InstallmentFormValues) {
    setFormError(null);
    try {
      await createInstallments.mutateAsync(values);
      onDone();
    } catch (err) {
      setFormError(getErrorMessage(err, "Não foi possível criar o parcelamento."));
    }
  }

  return (
    <>
      <DialogHeader>
        <DialogTitle>Parcelamento</DialogTitle>
        <DialogDescription>Cria todas as parcelas de uma vez, já efetivadas.</DialogDescription>
      </DialogHeader>

      <form className="space-y-4" onSubmit={(e) => void handleSubmit(onSubmit)(e)} noValidate>
        <div className="space-y-1.5">
          <Label htmlFor="installment-type">Tipo</Label>
          <NativeSelect
            id="installment-type"
            value={type}
            onChange={(e) => {
              setValue("type", e.target.value as EntryType);
              setValue("categoryId", undefined);
            }}
          >
            {ENTRY_TYPES.map((t) => (
              <option key={t} value={t}>
                {TRANSACTION_TYPE_LABELS[t]}
              </option>
            ))}
          </NativeSelect>
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="installment-account">Conta</Label>
          <AccountSelect
            id="installment-account"
            value={accountId}
            onValueChange={(v) => setValue("accountId", v ?? "", { shouldValidate: true })}
          />
          {errors.accountId && <p className="text-sm text-destructive">{errors.accountId.message}</p>}
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="installment-category">Categoria</Label>
          <CategorySelect
            id="installment-category"
            type={type}
            value={categoryId}
            onValueChange={(v) => setValue("categoryId", v)}
            clearable
          />
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="installment-description">Descrição</Label>
          <Input
            id="installment-description"
            autoComplete="off"
            aria-invalid={!!errors.description}
            {...register("description")}
          />
          {errors.description && <p className="text-sm text-destructive">{errors.description.message}</p>}
        </div>

        <div className="grid grid-cols-3 gap-3">
          <div className="space-y-1.5">
            <Label htmlFor="installment-amount">Valor da parcela</Label>
            <Input
              id="installment-amount"
              type="number"
              step="0.01"
              aria-invalid={!!errors.amountPerInstallment}
              {...register("amountPerInstallment", { valueAsNumber: true })}
            />
            {errors.amountPerInstallment && (
              <p className="text-sm text-destructive">{errors.amountPerInstallment.message}</p>
            )}
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="installment-count">Parcelas</Label>
            <Input
              id="installment-count"
              type="number"
              min={2}
              step="1"
              aria-invalid={!!errors.installments}
              {...register("installments", { valueAsNumber: true })}
            />
            {errors.installments && <p className="text-sm text-destructive">{errors.installments.message}</p>}
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="installment-date">1ª parcela</Label>
            <Input id="installment-date" type="date" aria-invalid={!!errors.firstTransactionDate} {...register("firstTransactionDate")} />
          </div>
        </div>

        {preview.length > 0 && (
          <div className="space-y-1.5">
            <Label>Prévia</Label>
            <div className="max-h-40 overflow-y-auto rounded-md border border-border">
              <table className="w-full text-xs">
                <tbody>
                  {preview.map((item) => (
                    <tr key={item.number} className="border-b border-border last:border-0">
                      <td className="py-1.5 pr-3 pl-3 text-muted-foreground">
                        {item.number}/{installments}
                      </td>
                      <td className="py-1.5 pr-3">{formatDate(item.date)}</td>
                      <td className="py-1.5 pr-3 text-muted-foreground">{formatYearMonth(item.month)}</td>
                      <td className="py-1.5 pr-3 text-right tabular-nums">{formatMoney(amountPerInstallment)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <p className="text-xs text-muted-foreground">
              Total: {formatMoney(amountPerInstallment * installments)} em {installments} parcelas.
            </p>
          </div>
        )}

        {formError && (
          <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
            {formError}
          </p>
        )}

        <DialogFooter>
          <Button type="submit" disabled={isSubmitting}>
            {isSubmitting ? "Criando…" : "Criar parcelamento"}
          </Button>
        </DialogFooter>
      </form>
    </>
  );
}
