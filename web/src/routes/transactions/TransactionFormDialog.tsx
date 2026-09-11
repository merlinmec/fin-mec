import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import type { EntryType, RecurrenceRule, Transaction } from "@/api/transactions";
import { ENTRY_TYPES, RECURRENCE_RULES, RECURRENCE_RULE_LABELS, TRANSACTION_TYPE_LABELS } from "@/api/transactions";
import { currentYearMonth, todayIso, yearMonthOf } from "@/lib/dates";
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
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { useCreateTransaction, useUpdateTransaction } from "./hooks";
import { entrySchema, type EntryFormValues } from "./transaction-schema";

interface TransactionFormDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Presente = editar esse lancamento; ausente = criar um novo (avulso — receita ou despesa). */
  transaction?: Transaction;
  /** Pre-seleciona a conta ao criar a partir do filtro ja ativo na lista. */
  defaultAccountId?: string;
}

export function TransactionFormDialog({ open, onOpenChange, transaction, defaultAccountId }: TransactionFormDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <EntryForm
          key={transaction?.id ?? "new"}
          transaction={transaction}
          defaultAccountId={defaultAccountId}
          onDone={() => onOpenChange(false)}
        />
      </DialogContent>
    </Dialog>
  );
}

function EntryForm({
  transaction,
  defaultAccountId,
  onDone,
}: {
  transaction?: Transaction;
  defaultAccountId?: string;
  onDone: () => void;
}) {
  const createTransaction = useCreateTransaction();
  const updateTransaction = useUpdateTransaction();
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<EntryFormValues>({
    resolver: zodResolver(entrySchema),
    defaultValues: transaction
      ? {
          accountId: transaction.accountId,
          categoryId: transaction.categoryId ?? undefined,
          type: transaction.type as EntryType,
          amount: transaction.amount,
          description: transaction.description,
          transactionDate: transaction.transactionDate,
          competenceMonth: transaction.competenceMonth,
          status: transaction.status === "CANCELED" ? "POSTED" : transaction.status,
          recurrenceRule: transaction.recurrenceRule ?? "",
        }
      : {
          accountId: defaultAccountId ?? "",
          categoryId: undefined,
          type: "EXPENSE",
          amount: 0,
          description: "",
          transactionDate: todayIso(),
          competenceMonth: currentYearMonth(),
          status: "POSTED",
          recurrenceRule: "",
        },
  });

  const type = watch("type");
  const accountId = watch("accountId");
  const categoryId = watch("categoryId");
  const transactionDate = watch("transactionDate");

  async function onSubmit(values: EntryFormValues) {
    setFormError(null);
    const payload = {
      accountId: values.accountId,
      categoryId: values.categoryId,
      type: values.type,
      amount: values.amount,
      description: values.description,
      transactionDate: values.transactionDate,
      competenceMonth: values.competenceMonth,
      status: values.status,
      recurrenceRule: values.recurrenceRule ? (values.recurrenceRule as RecurrenceRule) : undefined,
    };
    try {
      if (transaction) {
        await updateTransaction.mutateAsync({ id: transaction.id, payload });
      } else {
        await createTransaction.mutateAsync(payload);
      }
      onDone();
    } catch (err) {
      setFormError(getErrorMessage(err, "Não foi possível salvar o lançamento."));
    }
  }

  return (
    <>
      <DialogHeader>
        <DialogTitle>{transaction ? "Editar lançamento" : "Novo lançamento"}</DialogTitle>
      </DialogHeader>

      <form className="space-y-4" onSubmit={(e) => void handleSubmit(onSubmit)(e)} noValidate>
        <div className="space-y-1.5">
          <Label htmlFor="entry-type">Tipo</Label>
          <NativeSelect
            id="entry-type"
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
          <Label htmlFor="entry-account">Conta</Label>
          <AccountSelect id="entry-account" value={accountId} onValueChange={(v) => setValue("accountId", v ?? "", { shouldValidate: true })} />
          {errors.accountId && <p className="text-sm text-destructive">{errors.accountId.message}</p>}
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="entry-category">Categoria</Label>
          <CategorySelect
            id="entry-category"
            type={type}
            value={categoryId}
            onValueChange={(v) => setValue("categoryId", v)}
            clearable
            placeholder="Selecione uma categoria"
          />
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div className="space-y-1.5">
            <Label htmlFor="entry-amount">Valor</Label>
            <Input
              id="entry-amount"
              type="number"
              step="0.01"
              aria-invalid={!!errors.amount}
              {...register("amount", { valueAsNumber: true })}
            />
            {errors.amount && <p className="text-sm text-destructive">{errors.amount.message}</p>}
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="entry-status">Status</Label>
            <NativeSelect id="entry-status" aria-invalid={!!errors.status} {...register("status")}>
              <option value="POSTED">Efetivado</option>
              <option value="PENDING">Pendente</option>
            </NativeSelect>
          </div>
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="entry-description">Descrição</Label>
          <Input id="entry-description" autoComplete="off" aria-invalid={!!errors.description} {...register("description")} />
          {errors.description && <p className="text-sm text-destructive">{errors.description.message}</p>}
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div className="space-y-1.5">
            <Label htmlFor="entry-date">Data</Label>
            <Input
              id="entry-date"
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
            <Label htmlFor="entry-competence">Competência</Label>
            <Input id="entry-competence" type="month" aria-invalid={!!errors.competenceMonth} {...register("competenceMonth")} />
            {errors.competenceMonth && <p className="text-sm text-destructive">{errors.competenceMonth.message}</p>}
          </div>
        </div>
        {transactionDate && (
          <p className="text-xs text-muted-foreground">
            A competência é sugerida a partir da data, mas pode ser ajustada.
          </p>
        )}

        <div className="space-y-1.5">
          <Label htmlFor="entry-recurrence">Recorrência (informativo)</Label>
          <NativeSelect id="entry-recurrence" {...register("recurrenceRule")}>
            <option value="">Não recorrente</option>
            {RECURRENCE_RULES.map((r) => (
              <option key={r} value={r}>
                {RECURRENCE_RULE_LABELS[r]}
              </option>
            ))}
          </NativeSelect>
          <p className="text-xs text-muted-foreground">
            Só marca o lançamento como recorrente — o fin-mec ainda não gera as próximas ocorrências automaticamente.
          </p>
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
