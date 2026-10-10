import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { CheckCircle2 } from "lucide-react";
import type { CreditCardInvoice } from "@/api/creditCards";
import { formatDate, formatYearMonth, todayIso } from "@/lib/dates";
import { formatMoney } from "@/lib/money";
import { getErrorMessage } from "@/lib/errors";
import { AccountSelect } from "@/components/AccountSelect";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { FieldError } from "@/components/ui/field-error";
import { errorDescribedBy } from "@/lib/field-error";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { usePayInvoice } from "./hooks";
import { payInvoiceSchema, type PayInvoiceFormValues } from "./pay-invoice-schema";

interface PayInvoiceDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  invoice: CreditCardInvoice;
  cardId: string;
  defaultAccountId?: string;
}

export function PayInvoiceDialog({ open, onOpenChange, invoice, cardId, defaultAccountId }: PayInvoiceDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <PayInvoiceForm
          key={invoice.id}
          invoice={invoice}
          cardId={cardId}
          defaultAccountId={defaultAccountId}
          onClose={() => onOpenChange(false)}
        />
      </DialogContent>
    </Dialog>
  );
}

function PayInvoiceForm({
  invoice,
  cardId,
  defaultAccountId,
  onClose,
}: {
  invoice: CreditCardInvoice;
  cardId: string;
  defaultAccountId?: string;
  onClose: () => void;
}) {
  const payInvoice = usePayInvoice(cardId);
  const [formError, setFormError] = useState<string | null>(null);
  const [paidResult, setPaidResult] = useState<{ amount: number; date: string } | null>(null);

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<PayInvoiceFormValues>({
    resolver: zodResolver(payInvoiceSchema),
    defaultValues: {
      accountId: defaultAccountId ?? "",
      paymentDate: todayIso(),
      paidAmount: invoice.totalAmount,
    },
  });

  const accountId = watch("accountId");

  async function onSubmit(values: PayInvoiceFormValues) {
    setFormError(null);
    try {
      await payInvoice.mutateAsync({ id: invoice.id, payload: values });
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
          Lançamento criado: fatura de {formatYearMonth(invoice.referenceMonth)}, {formatMoney(paidResult.amount)} em{" "}
          {formatDate(paidResult.date)}.
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
        <DialogTitle>Pagar fatura</DialogTitle>
        <DialogDescription>
          {formatYearMonth(invoice.referenceMonth)} — {formatMoney(invoice.totalAmount)}, vencimento {formatDate(invoice.dueDate)}.
        </DialogDescription>
      </DialogHeader>

      <form className="space-y-4" onSubmit={(e) => void handleSubmit(onSubmit)(e)} noValidate>
        <div className="space-y-1.5">
          <Label htmlFor="invoice-pay-account">Conta de pagamento</Label>
          <AccountSelect
            id="invoice-pay-account"
            aria-describedby={errorDescribedBy("invoice-pay-account", errors.accountId)}
            aria-invalid={!!errors.accountId}
            value={accountId}
            onValueChange={(v) => setValue("accountId", v ?? "", { shouldValidate: true })}
          />
          <FieldError id="invoice-pay-account-error" error={errors.accountId} />
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div className="space-y-1.5">
            <Label htmlFor="invoice-pay-date">Data do pagamento</Label>
            <Input id="invoice-pay-date" aria-describedby={errorDescribedBy("invoice-pay-date", errors.paymentDate)} type="date" aria-invalid={!!errors.paymentDate} {...register("paymentDate")} />
            <FieldError id="invoice-pay-date-error" error={errors.paymentDate} />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="invoice-pay-amount">Valor pago</Label>
            <Input
              id="invoice-pay-amount"
              aria-describedby={errorDescribedBy("invoice-pay-amount", errors.paidAmount)}
              type="number"
              step="0.01"
              aria-invalid={!!errors.paidAmount}
              {...register("paidAmount", { valueAsNumber: true })}
            />
            <FieldError id="invoice-pay-amount-error" error={errors.paidAmount} />
          </div>
        </div>

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
