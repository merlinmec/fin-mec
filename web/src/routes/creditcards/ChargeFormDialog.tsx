import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import type { EntryType } from "@/api/transactions";
import { ENTRY_TYPES, TRANSACTION_TYPE_LABELS } from "@/api/transactions";
import { todayIso } from "@/lib/dates";
import { getErrorMessage } from "@/lib/errors";
import { CategorySelect } from "@/components/CategorySelect";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect } from "@/components/ui/native-select";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { useRegisterCharge } from "./hooks";
import { chargeSchema, type ChargeFormValues } from "./charge-schema";

interface ChargeFormDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  cardId: string;
}

/**
 * Lançar compra (ou estorno) no cartão — a fatura e resolvida automaticamente
 * pelo backend a partir da data da compra + dia de fechamento do cartao,
 * entao o form nunca pede pra escolher a fatura. Parcelamento e um checkbox
 * aqui (nao um dialog separado como em Transaction/FE-4) porque o backend ja
 * junta avulso+parcelado no mesmo endpoint (registerCharge).
 */
export function ChargeFormDialog({ open, onOpenChange, cardId }: ChargeFormDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <ChargeForm key={open ? "open" : "closed"} cardId={cardId} onDone={() => onOpenChange(false)} />
      </DialogContent>
    </Dialog>
  );
}

function ChargeForm({ cardId, onDone }: { cardId: string; onDone: () => void }) {
  const registerCharge = useRegisterCharge(cardId);
  const [formError, setFormError] = useState<string | null>(null);
  const [installmentEnabled, setInstallmentEnabled] = useState(false);

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<ChargeFormValues>({
    resolver: zodResolver(chargeSchema),
    defaultValues: {
      description: "",
      amount: 0,
      type: "EXPENSE",
      categoryId: undefined,
      purchaseDate: todayIso(),
      installments: undefined,
    },
  });

  const type = watch("type");
  const categoryId = watch("categoryId");

  async function onSubmit(values: ChargeFormValues) {
    setFormError(null);
    const payload = { ...values, installments: installmentEnabled ? values.installments : undefined };
    try {
      await registerCharge.mutateAsync(payload);
      onDone();
    } catch (err) {
      setFormError(getErrorMessage(err, "Não foi possível lançar a compra."));
    }
  }

  return (
    <>
      <DialogHeader>
        <DialogTitle>Lançar no cartão</DialogTitle>
        <DialogDescription>A fatura é resolvida automaticamente pela data da compra.</DialogDescription>
      </DialogHeader>

      <form className="space-y-4" onSubmit={(e) => void handleSubmit(onSubmit)(e)} noValidate>
        <div className="space-y-1.5">
          <Label htmlFor="charge-type">Tipo</Label>
          <NativeSelect
            id="charge-type"
            value={type}
            onChange={(e) => {
              setValue("type", e.target.value as EntryType);
              setValue("categoryId", undefined);
            }}
          >
            {ENTRY_TYPES.map((t) => (
              <option key={t} value={t}>
                {t === "EXPENSE" ? "Compra" : "Estorno"} ({TRANSACTION_TYPE_LABELS[t]})
              </option>
            ))}
          </NativeSelect>
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="charge-description">Descrição</Label>
          <Input id="charge-description" autoComplete="off" aria-invalid={!!errors.description} {...register("description")} />
          {errors.description && <p className="text-sm text-destructive">{errors.description.message}</p>}
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="charge-category">Categoria</Label>
          <CategorySelect id="charge-category" type={type} value={categoryId} onValueChange={(v) => setValue("categoryId", v)} clearable />
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div className="space-y-1.5">
            <Label htmlFor="charge-amount">{installmentEnabled ? "Valor da parcela" : "Valor"}</Label>
            <Input
              id="charge-amount"
              type="number"
              step="0.01"
              aria-invalid={!!errors.amount}
              {...register("amount", { valueAsNumber: true })}
            />
            {errors.amount && <p className="text-sm text-destructive">{errors.amount.message}</p>}
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="charge-date">Data da compra</Label>
            <Input id="charge-date" type="date" aria-invalid={!!errors.purchaseDate} {...register("purchaseDate")} />
            {errors.purchaseDate && <p className="text-sm text-destructive">{errors.purchaseDate.message}</p>}
          </div>
        </div>

        <div className="space-y-2">
          <div className="flex items-center gap-2">
            <input
              id="charge-installment-toggle"
              type="checkbox"
              className="size-4 rounded border-input"
              checked={installmentEnabled}
              onChange={(e) => {
                setInstallmentEnabled(e.target.checked);
                setValue("installments", e.target.checked ? 2 : undefined);
              }}
            />
            <Label htmlFor="charge-installment-toggle" className="font-normal">
              Parcelar
            </Label>
          </div>
          {installmentEnabled && (
            <div className="space-y-1.5">
              <Label htmlFor="charge-installments">Número de parcelas</Label>
              <Input
                id="charge-installments"
                type="number"
                min={2}
                step="1"
                aria-invalid={!!errors.installments}
                {...register("installments", { valueAsNumber: true })}
              />
              {errors.installments && <p className="text-sm text-destructive">{errors.installments.message}</p>}
            </div>
          )}
        </div>

        {formError && (
          <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
            {formError}
          </p>
        )}

        <DialogFooter>
          <Button type="submit" disabled={isSubmitting}>
            {isSubmitting ? "Lançando…" : "Lançar"}
          </Button>
        </DialogFooter>
      </form>
    </>
  );
}
