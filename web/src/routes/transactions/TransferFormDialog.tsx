import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { currentYearMonth, todayIso, yearMonthOf } from "@/lib/dates";
import { getErrorMessage } from "@/lib/errors";
import { AccountSelect } from "@/components/AccountSelect";
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
import { useCreateTransfer } from "./hooks";
import { transferSchema, type TransferFormValues } from "./transaction-schema";

interface TransferFormDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

/** Transferencia entre contas — as duas pernas (saida/entrada) nascem juntas, atomicamente, no backend. */
export function TransferFormDialog({ open, onOpenChange }: TransferFormDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <TransferForm key={open ? "open" : "closed"} onDone={() => onOpenChange(false)} />
      </DialogContent>
    </Dialog>
  );
}

function TransferForm({ onDone }: { onDone: () => void }) {
  const createTransfer = useCreateTransfer();
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<TransferFormValues>({
    resolver: zodResolver(transferSchema),
    defaultValues: {
      sourceAccountId: "",
      destinationAccountId: "",
      amount: 0,
      description: "",
      transactionDate: todayIso(),
      competenceMonth: currentYearMonth(),
      status: "POSTED",
    },
  });

  const sourceAccountId = watch("sourceAccountId");
  const destinationAccountId = watch("destinationAccountId");

  async function onSubmit(values: TransferFormValues) {
    setFormError(null);
    try {
      await createTransfer.mutateAsync(values);
      onDone();
    } catch (err) {
      setFormError(getErrorMessage(err, "Não foi possível registrar a transferência."));
    }
  }

  return (
    <>
      <DialogHeader>
        <DialogTitle>Transferência entre contas</DialogTitle>
        <DialogDescription>Cria duas pernas pareadas: saída na origem, entrada no destino.</DialogDescription>
      </DialogHeader>

      <form className="space-y-4" onSubmit={(e) => void handleSubmit(onSubmit)(e)} noValidate>
        <div className="space-y-1.5">
          <Label htmlFor="transfer-source">De</Label>
          <AccountSelect
            id="transfer-source"
            value={sourceAccountId}
            onValueChange={(v) => setValue("sourceAccountId", v ?? "", { shouldValidate: true })}
          />
          {errors.sourceAccountId && <p className="text-sm text-destructive">{errors.sourceAccountId.message}</p>}
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="transfer-destination">Para</Label>
          <AccountSelect
            id="transfer-destination"
            value={destinationAccountId}
            onValueChange={(v) => setValue("destinationAccountId", v ?? "", { shouldValidate: true })}
          />
          {errors.destinationAccountId && (
            <p className="text-sm text-destructive">{errors.destinationAccountId.message}</p>
          )}
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div className="space-y-1.5">
            <Label htmlFor="transfer-amount">Valor</Label>
            <Input
              id="transfer-amount"
              type="number"
              step="0.01"
              aria-invalid={!!errors.amount}
              {...register("amount", { valueAsNumber: true })}
            />
            {errors.amount && <p className="text-sm text-destructive">{errors.amount.message}</p>}
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="transfer-status">Status</Label>
            <NativeSelect id="transfer-status" {...register("status")}>
              <option value="POSTED">Efetivado</option>
              <option value="PENDING">Pendente</option>
            </NativeSelect>
          </div>
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="transfer-description">Descrição</Label>
          <Input
            id="transfer-description"
            autoComplete="off"
            aria-invalid={!!errors.description}
            {...register("description")}
          />
          {errors.description && <p className="text-sm text-destructive">{errors.description.message}</p>}
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div className="space-y-1.5">
            <Label htmlFor="transfer-date">Data</Label>
            <Input
              id="transfer-date"
              type="date"
              aria-invalid={!!errors.transactionDate}
              {...register("transactionDate", {
                onChange: (e: React.ChangeEvent<HTMLInputElement>) => {
                  if (e.target.value) setValue("competenceMonth", yearMonthOf(e.target.value));
                },
              })}
            />
            {errors.transactionDate && <p className="text-sm text-destructive">{errors.transactionDate.message}</p>}
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="transfer-competence">Competência</Label>
            <Input id="transfer-competence" type="month" aria-invalid={!!errors.competenceMonth} {...register("competenceMonth")} />
            {errors.competenceMonth && <p className="text-sm text-destructive">{errors.competenceMonth.message}</p>}
          </div>
        </div>

        {formError && (
          <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
            {formError}
          </p>
        )}

        <DialogFooter>
          <Button type="submit" disabled={isSubmitting}>
            {isSubmitting ? "Registrando…" : "Registrar transferência"}
          </Button>
        </DialogFooter>
      </form>
    </>
  );
}
