import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import type { Account } from "@/api/accounts";
import { ACCOUNT_TYPES, ACCOUNT_TYPE_LABELS } from "@/api/accounts";
import { getErrorMessage } from "@/lib/errors";
import { formatMoney } from "@/lib/money";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select } from "@/components/ui/select";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { useCreateAccount, useUpdateAccount } from "./hooks";
import { createAccountSchema, updateAccountSchema, type CreateAccountFormValues, type UpdateAccountFormValues } from "./account-schema";

interface AccountFormDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Presente = editar essa conta; ausente = criar uma nova. */
  account?: Account;
}

/**
 * Um so Dialog, mas o formulario de dentro muda de forma: criar tem
 * initialBalance editavel (o backend so aceita esse campo na criacao),
 * editar troca por um toggle de arquivada e mostra o saldo inicial so
 * como referencia. Duas funcoes de formulario internas (nao dois
 * componentes exportados) para o React trocar de uma pra outra sem
 * reconciliar campos incompativeis no mesmo <form>.
 */
export function AccountFormDialog({ open, onOpenChange, account }: AccountFormDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        {account ? (
          <EditAccountForm key={account.id} account={account} onDone={() => onOpenChange(false)} />
        ) : (
          <CreateAccountForm key="new" onDone={() => onOpenChange(false)} />
        )}
      </DialogContent>
    </Dialog>
  );
}

function CreateAccountForm({ onDone }: { onDone: () => void }) {
  const createAccount = useCreateAccount();
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<CreateAccountFormValues>({
    resolver: zodResolver(createAccountSchema),
    defaultValues: { name: "", type: "CHECKING", initialBalance: 0 },
  });

  async function onSubmit(values: CreateAccountFormValues) {
    setFormError(null);
    try {
      await createAccount.mutateAsync(values);
      onDone();
    } catch (err) {
      setFormError(getErrorMessage(err, "Não foi possível criar a conta."));
    }
  }

  return (
    <>
      <DialogHeader>
        <DialogTitle>Nova conta</DialogTitle>
        <DialogDescription>Contas manuais — corrente, poupança, carteira ou investimento.</DialogDescription>
      </DialogHeader>

      <form className="space-y-4" onSubmit={(e) => void handleSubmit(onSubmit)(e)} noValidate>
        <div className="space-y-1.5">
          <Label htmlFor="name">Nome</Label>
          <Input id="name" autoComplete="off" aria-invalid={!!errors.name} {...register("name")} />
          {errors.name && <p className="text-sm text-destructive">{errors.name.message}</p>}
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="type">Tipo</Label>
          <Select id="type" aria-invalid={!!errors.type} {...register("type")}>
            {ACCOUNT_TYPES.map((type) => (
              <option key={type} value={type}>
                {ACCOUNT_TYPE_LABELS[type]}
              </option>
            ))}
          </Select>
          {errors.type && <p className="text-sm text-destructive">{errors.type.message}</p>}
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="initialBalance">Saldo inicial</Label>
          <Input
            id="initialBalance"
            type="number"
            step="0.01"
            aria-invalid={!!errors.initialBalance}
            {...register("initialBalance", { valueAsNumber: true })}
          />
          {errors.initialBalance && <p className="text-sm text-destructive">{errors.initialBalance.message}</p>}
        </div>

        {formError && (
          <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
            {formError}
          </p>
        )}

        <DialogFooter>
          <Button type="submit" disabled={isSubmitting}>
            {isSubmitting ? "Criando…" : "Criar conta"}
          </Button>
        </DialogFooter>
      </form>
    </>
  );
}

function EditAccountForm({ account, onDone }: { account: Account; onDone: () => void }) {
  const updateAccount = useUpdateAccount();
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<UpdateAccountFormValues>({
    resolver: zodResolver(updateAccountSchema),
    defaultValues: { name: account.name, type: account.type, archived: account.archived },
  });

  async function onSubmit(values: UpdateAccountFormValues) {
    setFormError(null);
    try {
      await updateAccount.mutateAsync({ id: account.id, payload: values });
      onDone();
    } catch (err) {
      setFormError(getErrorMessage(err, "Não foi possível atualizar a conta."));
    }
  }

  return (
    <>
      <DialogHeader>
        <DialogTitle>Editar conta</DialogTitle>
        <DialogDescription>
          Saldo inicial: {formatMoney(account.initialBalance)} (definido na criação, não editável).
        </DialogDescription>
      </DialogHeader>

      <form className="space-y-4" onSubmit={(e) => void handleSubmit(onSubmit)(e)} noValidate>
        <div className="space-y-1.5">
          <Label htmlFor="edit-name">Nome</Label>
          <Input id="edit-name" autoComplete="off" aria-invalid={!!errors.name} {...register("name")} />
          {errors.name && <p className="text-sm text-destructive">{errors.name.message}</p>}
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="edit-type">Tipo</Label>
          <Select id="edit-type" aria-invalid={!!errors.type} {...register("type")}>
            {ACCOUNT_TYPES.map((type) => (
              <option key={type} value={type}>
                {ACCOUNT_TYPE_LABELS[type]}
              </option>
            ))}
          </Select>
          {errors.type && <p className="text-sm text-destructive">{errors.type.message}</p>}
        </div>

        <div className="flex items-center gap-2">
          <input id="edit-archived" type="checkbox" className="size-4 rounded border-input" {...register("archived")} />
          <Label htmlFor="edit-archived" className="font-normal">
            Arquivada
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
