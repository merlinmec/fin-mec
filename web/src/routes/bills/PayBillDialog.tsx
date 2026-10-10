import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { CheckCircle2 } from "lucide-react";
import type { Bill } from "@/api/bills";
import { formatDate, todayIso } from "@/lib/dates";
import { formatMoney } from "@/lib/money";
import { getErrorMessage } from "@/lib/errors";
import { AccountSelect } from "@/components/AccountSelect";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { FieldError } from "@/components/ui/field-error";
import { errorDescribedBy } from "@/lib/field-error";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { usePayBill } from "./hooks";
import { payBillSchema, type PayBillFormValues } from "./bill-schema";

interface PayBillDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  bill: Bill | undefined;
}

export function PayBillDialog({ open, onOpenChange, bill }: PayBillDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>{bill && <PayBillForm key={bill.id} bill={bill} onClose={() => onOpenChange(false)} />}</DialogContent>
    </Dialog>
  );
}

function PayBillForm({ bill, onClose }: { bill: Bill; onClose: () => void }) {
  const payBill = usePayBill();
  const [formError, setFormError] = useState<string | null>(null);
  const [paidResult, setPaidResult] = useState<{ amount: number; date: string } | null>(null);

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<PayBillFormValues>({
    resolver: zodResolver(payBillSchema),
    defaultValues: {
      accountId: bill.sourceAccountId ?? "",
      paymentDate: todayIso(),
      paidAmount: bill.amount,
    },
  });

  const accountId = watch("accountId");

  async function onSubmit(values: PayBillFormValues) {
    setFormError(null);
    try {
      await payBill.mutateAsync({ id: bill.id, payload: values });
      setPaidResult({ amount: values.paidAmount, date: values.paymentDate });
    } catch (err) {
      setFormError(getErrorMessage(err, "Não foi possível registrar o pagamento."));
    }
  }

  if (paidResult) {
    return (
      <div className="space-y-4">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <CheckCircle2 className="size-5 text-success" />
            Pagamento registrado
          </DialogTitle>
        </DialogHeader>
        <p className="text-sm text-muted-foreground">
          Lançamento criado: <span className="font-medium text-foreground">{bill.description}</span>,{" "}
          {formatMoney(paidResult.amount)} em {formatDate(paidResult.date)}.
        </p>
        <DialogFooter>
          <Button onClick={onClose}>Fechar</Button>
        </DialogFooter>
      </div>
    );
  }

  return (
    <>
      <DialogHeader>
        <DialogTitle>Dar baixa</DialogTitle>
        <DialogDescription>
          {bill.description} — {formatMoney(bill.amount)}, vencimento {formatDate(bill.dueDate)}.
        </DialogDescription>
      </DialogHeader>

      <form className="space-y-4" onSubmit={(e) => void handleSubmit(onSubmit)(e)} noValidate>
        <div className="space-y-1.5">
          <Label htmlFor="pay-account">Conta de pagamento</Label>
          <AccountSelect id="pay-account" aria-describedby={errorDescribedBy("pay-account", errors.accountId)} aria-invalid={!!errors.accountId} value={accountId} onValueChange={(v) => setValue("accountId", v ?? "", { shouldValidate: true })} />
          <FieldError id="pay-account-error" error={errors.accountId} />
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div className="space-y-1.5">
            <Label htmlFor="pay-date">Data do pagamento</Label>
            <Input id="pay-date" aria-describedby={errorDescribedBy("pay-date", errors.paymentDate)} type="date" aria-invalid={!!errors.paymentDate} {...register("paymentDate")} />
            <FieldError id="pay-date-error" error={errors.paymentDate} />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="pay-amount">Valor pago</Label>
            <Input
              id="pay-amount"
              aria-describedby={errorDescribedBy("pay-amount", errors.paidAmount)}
              type="number"
              step="0.01"
              aria-invalid={!!errors.paidAmount}
              {...register("paidAmount", { valueAsNumber: true })}
            />
            <FieldError id="pay-amount-error" error={errors.paidAmount} />
          </div>
        </div>
        <p className="text-xs text-muted-foreground">Diferente do valor planejado? Ajuste aqui — o lançamento usa o valor pago, não o previsto.</p>

        {formError && (
          <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
            {formError}
          </p>
        )}

        <DialogFooter>
          <Button type="submit" disabled={isSubmitting}>
            {isSubmitting ? "Registrando…" : "Confirmar pagamento"}
          </Button>
        </DialogFooter>
      </form>
    </>
  );
}
