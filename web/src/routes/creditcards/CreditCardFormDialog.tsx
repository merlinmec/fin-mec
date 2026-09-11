import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import type { CreditCard } from "@/api/creditCards";
import { getErrorMessage } from "@/lib/errors";
import { AccountSelect } from "@/components/AccountSelect";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { useCreateCreditCard, useUpdateCreditCard } from "./hooks";
import { createCreditCardSchema, updateCreditCardSchema, type CreateCreditCardFormValues, type UpdateCreditCardFormValues } from "./creditcard-schema";

interface CreditCardFormDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  card?: CreditCard;
}

/** Mesma assimetria de AccountFormDialog (FE-2): criar nao tem "archived", editar tem. */
export function CreditCardFormDialog({ open, onOpenChange, card }: CreditCardFormDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        {card ? (
          <EditCardForm key={card.id} card={card} onDone={() => onOpenChange(false)} />
        ) : (
          <CreateCardForm key="new" onDone={() => onOpenChange(false)} />
        )}
      </DialogContent>
    </Dialog>
  );
}

function CreateCardForm({ onDone }: { onDone: () => void }) {
  const createCard = useCreateCreditCard();
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<CreateCreditCardFormValues>({
    resolver: zodResolver(createCreditCardSchema),
    defaultValues: { name: "", creditLimit: 0, closingDay: 1, dueDay: 10, paymentAccountId: undefined },
  });

  const paymentAccountId = watch("paymentAccountId");

  async function onSubmit(values: CreateCreditCardFormValues) {
    setFormError(null);
    try {
      await createCard.mutateAsync(values);
      onDone();
    } catch (err) {
      setFormError(getErrorMessage(err, "Não foi possível criar o cartão."));
    }
  }

  return (
    <>
      <DialogHeader>
        <DialogTitle>Novo cartão</DialogTitle>
      </DialogHeader>
      <form className="space-y-4" onSubmit={(e) => void handleSubmit(onSubmit)(e)} noValidate>
        <div className="space-y-1.5">
          <Label htmlFor="card-name">Nome</Label>
          <Input id="card-name" autoComplete="off" aria-invalid={!!errors.name} {...register("name")} />
          {errors.name && <p className="text-sm text-destructive">{errors.name.message}</p>}
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="card-limit">Limite</Label>
          <Input
            id="card-limit"
            type="number"
            step="0.01"
            aria-invalid={!!errors.creditLimit}
            {...register("creditLimit", { valueAsNumber: true })}
          />
          {errors.creditLimit && <p className="text-sm text-destructive">{errors.creditLimit.message}</p>}
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div className="space-y-1.5">
            <Label htmlFor="card-closing-day">Dia de fechamento</Label>
            <Input
              id="card-closing-day"
              type="number"
              min={1}
              max={31}
              aria-invalid={!!errors.closingDay}
              {...register("closingDay", { valueAsNumber: true })}
            />
            {errors.closingDay && <p className="text-sm text-destructive">{errors.closingDay.message}</p>}
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="card-due-day">Dia de vencimento</Label>
            <Input
              id="card-due-day"
              type="number"
              min={1}
              max={31}
              aria-invalid={!!errors.dueDay}
              {...register("dueDay", { valueAsNumber: true })}
            />
            {errors.dueDay && <p className="text-sm text-destructive">{errors.dueDay.message}</p>}
          </div>
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="card-account">Conta de pagamento (opcional)</Label>
          <AccountSelect
            id="card-account"
            value={paymentAccountId}
            onValueChange={(v) => setValue("paymentAccountId", v)}
            clearable
            clearLabel="Sem conta padrão"
            placeholder="Sem conta padrão"
          />
          <p className="text-xs text-muted-foreground">Usada como sugestão ao pagar a fatura — pode ser trocada na hora.</p>
        </div>

        {formError && (
          <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
            {formError}
          </p>
        )}

        <DialogFooter>
          <Button type="submit" disabled={isSubmitting}>
            {isSubmitting ? "Criando…" : "Criar cartão"}
          </Button>
        </DialogFooter>
      </form>
    </>
  );
}

function EditCardForm({ card, onDone }: { card: CreditCard; onDone: () => void }) {
  const updateCard = useUpdateCreditCard();
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<UpdateCreditCardFormValues>({
    resolver: zodResolver(updateCreditCardSchema),
    defaultValues: {
      name: card.name,
      creditLimit: card.creditLimit,
      closingDay: card.closingDay,
      dueDay: card.dueDay,
      paymentAccountId: card.paymentAccountId ?? undefined,
      archived: card.archived,
    },
  });

  const paymentAccountId = watch("paymentAccountId");

  async function onSubmit(values: UpdateCreditCardFormValues) {
    setFormError(null);
    try {
      await updateCard.mutateAsync({ id: card.id, payload: values });
      onDone();
    } catch (err) {
      setFormError(getErrorMessage(err, "Não foi possível atualizar o cartão."));
    }
  }

  return (
    <>
      <DialogHeader>
        <DialogTitle>Editar cartão</DialogTitle>
      </DialogHeader>
      <form className="space-y-4" onSubmit={(e) => void handleSubmit(onSubmit)(e)} noValidate>
        <div className="space-y-1.5">
          <Label htmlFor="edit-card-name">Nome</Label>
          <Input id="edit-card-name" autoComplete="off" aria-invalid={!!errors.name} {...register("name")} />
          {errors.name && <p className="text-sm text-destructive">{errors.name.message}</p>}
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="edit-card-limit">Limite</Label>
          <Input
            id="edit-card-limit"
            type="number"
            step="0.01"
            aria-invalid={!!errors.creditLimit}
            {...register("creditLimit", { valueAsNumber: true })}
          />
          {errors.creditLimit && <p className="text-sm text-destructive">{errors.creditLimit.message}</p>}
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div className="space-y-1.5">
            <Label htmlFor="edit-card-closing-day">Dia de fechamento</Label>
            <Input
              id="edit-card-closing-day"
              type="number"
              min={1}
              max={31}
              aria-invalid={!!errors.closingDay}
              {...register("closingDay", { valueAsNumber: true })}
            />
            {errors.closingDay && <p className="text-sm text-destructive">{errors.closingDay.message}</p>}
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="edit-card-due-day">Dia de vencimento</Label>
            <Input
              id="edit-card-due-day"
              type="number"
              min={1}
              max={31}
              aria-invalid={!!errors.dueDay}
              {...register("dueDay", { valueAsNumber: true })}
            />
            {errors.dueDay && <p className="text-sm text-destructive">{errors.dueDay.message}</p>}
          </div>
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="edit-card-account">Conta de pagamento (opcional)</Label>
          <AccountSelect
            id="edit-card-account"
            value={paymentAccountId}
            onValueChange={(v) => setValue("paymentAccountId", v)}
            clearable
            clearLabel="Sem conta padrão"
            placeholder="Sem conta padrão"
          />
        </div>

        <div className="flex items-center gap-2">
          <input id="card-archived" type="checkbox" className="size-4 rounded border-input" {...register("archived")} />
          <Label htmlFor="card-archived" className="font-normal">
            Arquivado
          </Label>
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
