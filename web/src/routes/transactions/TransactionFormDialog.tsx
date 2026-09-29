import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Repeat } from "lucide-react";
import type { EditScope, EntryType, RecurrenceRule, Transaction } from "@/api/transactions";
import { RECURRENCE_RULES, RECURRENCE_RULE_LABELS } from "@/api/transactions";
import { currentYearMonth, todayIso, yearMonthOf } from "@/lib/dates";
import { getErrorMessage } from "@/lib/errors";
import { cn } from "@/lib/utils";
import { AccountSelect } from "@/components/AccountSelect";
import { CategorySelect } from "@/components/CategorySelect";
import { TagPicker } from "@/components/TagPicker";
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
import { useCreateTransaction, useUpdateTransaction } from "./hooks";
import { entrySchema, type EntryFormValues } from "./transaction-schema";

interface TransactionFormDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Presente = editar esse lancamento; ausente = criar um novo (avulso ou fixo). */
  transaction?: Transaction;
  /** Pre-seleciona a conta ao criar a partir do filtro ja ativo na lista. */
  defaultAccountId?: string;
  /** Tipo inicial ao criar (ex.: atalho "Nova receita" da paleta de comandos). */
  defaultType?: EntryType;
}

export function TransactionFormDialog({
  open,
  onOpenChange,
  transaction,
  defaultAccountId,
  defaultType,
}: TransactionFormDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[92dvh] overflow-y-auto sm:max-w-lg">
        <EntryForm
          key={transaction?.id ?? `new-${defaultType ?? ""}`}
          transaction={transaction}
          defaultAccountId={defaultAccountId}
          defaultType={defaultType}
          onDone={() => onOpenChange(false)}
        />
      </DialogContent>
    </Dialog>
  );
}

const TYPE_TABS: { value: EntryType; label: string; active: string }[] = [
  { value: "EXPENSE", label: "Despesa", active: "bg-card text-destructive shadow-card" },
  { value: "INCOME", label: "Receita", active: "bg-card text-success shadow-card" },
];

function EntryForm({
  transaction,
  defaultAccountId,
  defaultType,
  onDone,
}: {
  transaction?: Transaction;
  defaultAccountId?: string;
  defaultType?: EntryType;
  onDone: () => void;
}) {
  const createTransaction = useCreateTransaction();
  const updateTransaction = useUpdateTransaction();
  const [formError, setFormError] = useState<string | null>(null);
  const isOccurrence = transaction?.recurrenceSeriesId != null;

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
          recurrenceEndDate: "",
          tagIds: transaction.tagIds ?? [],
        }
      : {
          accountId: defaultAccountId ?? "",
          categoryId: undefined,
          type: defaultType ?? "EXPENSE",
          amount: undefined as unknown as number,
          description: "",
          transactionDate: todayIso(),
          competenceMonth: currentYearMonth(),
          status: "POSTED",
          recurrenceRule: "",
          recurrenceEndDate: "",
          tagIds: [],
        },
  });

  const type = watch("type");
  const accountId = watch("accountId");
  const categoryId = watch("categoryId");
  const recurrenceRule = watch("recurrenceRule");
  const tagIds = watch("tagIds");

  async function save(values: EntryFormValues, scope: EditScope) {
    setFormError(null);
    const recurrence = values.recurrenceRule
      ? (values.recurrenceRule as RecurrenceRule)
      : undefined;
    try {
      if (transaction) {
        await updateTransaction.mutateAsync({
          id: transaction.id,
          scope,
          payload: {
            categoryId: values.categoryId,
            type: values.type,
            amount: values.amount,
            description: values.description,
            transactionDate: values.transactionDate,
            competenceMonth: values.competenceMonth,
            status: values.status,
            recurrenceRule: recurrence,
            tagIds: values.tagIds,
          },
        });
      } else {
        await createTransaction.mutateAsync({
          accountId: values.accountId,
          categoryId: values.categoryId,
          type: values.type,
          amount: values.amount,
          description: values.description,
          transactionDate: values.transactionDate,
          competenceMonth: values.competenceMonth,
          status: values.status,
          recurrenceRule: recurrence,
          recurrenceEndDate:
            recurrence && values.recurrenceEndDate ? values.recurrenceEndDate : undefined,
          tagIds: values.tagIds,
        });
      }
      onDone();
    } catch (err) {
      setFormError(getErrorMessage(err, "Não foi possível salvar o lançamento."));
    }
  }

  const submit = (scope: EditScope) => (e?: React.BaseSyntheticEvent) =>
    void handleSubmit((v) => save(v, scope))(e);

  return (
    <>
      <DialogHeader>
        <DialogTitle>{transaction ? "Editar lançamento" : "Novo lançamento"}</DialogTitle>
        {isOccurrence && transaction?.recurrenceRule && (
          <DialogDescription className="flex items-center gap-1.5">
            <Repeat className="size-3.5" />
            Lançamento fixo {RECURRENCE_RULE_LABELS[transaction.recurrenceRule].toLowerCase()}
          </DialogDescription>
        )}
      </DialogHeader>

      <form className="space-y-4" onSubmit={submit("THIS")} noValidate>
        <div
          className="grid grid-cols-2 gap-1 rounded-lg bg-muted p-1"
          role="radiogroup"
          aria-label="Tipo"
        >
          {TYPE_TABS.map((tab) => (
            <button
              key={tab.value}
              type="button"
              role="radio"
              aria-checked={type === tab.value}
              onClick={() => {
                if (type !== tab.value) {
                  setValue("type", tab.value);
                  setValue("categoryId", undefined);
                }
              }}
              className={cn(
                "h-8 rounded-md text-sm font-semibold transition-all",
                type === tab.value ? tab.active : "text-muted-foreground hover:text-foreground",
              )}
            >
              {tab.label}
            </button>
          ))}
        </div>

        <div className="grid grid-cols-[1fr_auto] items-end gap-3">
          <div className="space-y-1.5">
            <Label htmlFor="entry-amount">Valor</Label>
            <div className="relative">
              <span className="pointer-events-none absolute top-1/2 left-3 -translate-y-1/2 text-sm text-muted-foreground">
                R$
              </span>
              <Input
                id="entry-amount"
                type="number"
                inputMode="decimal"
                step="0.01"
                placeholder="0,00"
                autoFocus={!transaction}
                className="num h-11 pl-10 text-lg font-semibold"
                aria-invalid={!!errors.amount}
                {...register("amount", { valueAsNumber: true })}
              />
            </div>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="entry-status">Situação</Label>
            <NativeSelect
              id="entry-status"
              className="h-11"
              aria-invalid={!!errors.status}
              {...register("status")}
            >
              <option value="POSTED">{type === "INCOME" ? "Recebido" : "Pago"}</option>
              <option value="PENDING">Previsto</option>
            </NativeSelect>
          </div>
        </div>
        {errors.amount && <p className="-mt-2 text-sm text-destructive">{errors.amount.message}</p>}

        <div className="space-y-1.5">
          <Label htmlFor="entry-description">Descrição</Label>
          <Input
            id="entry-description"
            autoComplete="off"
            placeholder={type === "INCOME" ? "Ex.: Salário" : "Ex.: Mercado"}
            aria-invalid={!!errors.description}
            {...register("description")}
          />
          {errors.description && (
            <p className="text-sm text-destructive">{errors.description.message}</p>
          )}
        </div>

        <div className="grid gap-3 sm:grid-cols-2">
          <div className="space-y-1.5">
            <Label htmlFor="entry-account">Conta</Label>
            {transaction ? (
              <AccountSelect
                id="entry-account"
                value={accountId}
                onValueChange={() => undefined}
                includeArchived
                disabled
              />
            ) : (
              <AccountSelect
                id="entry-account"
                value={accountId}
                onValueChange={(v) => setValue("accountId", v ?? "", { shouldValidate: true })}
              />
            )}
            {errors.accountId && (
              <p className="text-sm text-destructive">{errors.accountId.message}</p>
            )}
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="entry-category">Categoria</Label>
            <CategorySelect
              id="entry-category"
              type={type}
              value={categoryId}
              onValueChange={(v) => setValue("categoryId", v)}
              clearable
              placeholder="Sem categoria"
            />
          </div>
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
            {errors.transactionDate && (
              <p className="text-sm text-destructive">{errors.transactionDate.message}</p>
            )}
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="entry-competence">Competência</Label>
            <Input
              id="entry-competence"
              type="month"
              aria-invalid={!!errors.competenceMonth}
              {...register("competenceMonth")}
            />
          </div>
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="entry-tags">Tags</Label>
          <TagPicker id="entry-tags" value={tagIds} onChange={(ids) => setValue("tagIds", ids)} />
        </div>

        {!isOccurrence && (
          <div className="space-y-3 rounded-xl border border-border bg-surface-2 p-3">
            <div className="grid gap-3 sm:grid-cols-2">
              <div className="space-y-1.5">
                <Label htmlFor="entry-recurrence" className="flex items-center gap-1.5">
                  <Repeat className="size-3.5" /> Repetir
                </Label>
                <NativeSelect id="entry-recurrence" {...register("recurrenceRule")}>
                  <option value="">Não repetir</option>
                  {RECURRENCE_RULES.map((r) => (
                    <option key={r} value={r}>
                      {RECURRENCE_RULE_LABELS[r]}
                    </option>
                  ))}
                </NativeSelect>
              </div>
              {recurrenceRule && !transaction && (
                <div className="space-y-1.5">
                  <Label htmlFor="entry-recurrence-end">Até (opcional)</Label>
                  <Input id="entry-recurrence-end" type="date" {...register("recurrenceEndDate")} />
                </div>
              )}
            </div>
            {recurrenceRule && (
              <p className="text-xs text-muted-foreground">
                As próximas ocorrências entram como{" "}
                <strong className="font-semibold text-foreground">previstas</strong> nos próximos 12
                meses: aparecem na previsão de saldo e você efetiva cada uma quando o dinheiro de
                fato sair ou entrar.
              </p>
            )}
          </div>
        )}

        {formError && (
          <p
            role="alert"
            className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive"
          >
            {formError}
          </p>
        )}

        <DialogFooter>
          {isOccurrence ? (
            <>
              <Button
                type="button"
                variant="outline"
                disabled={isSubmitting}
                onClick={submit("THIS_AND_FUTURE")}
              >
                Salvar este e os próximos
              </Button>
              <Button type="submit" disabled={isSubmitting}>
                {isSubmitting ? "Salvando…" : "Salvar só este"}
              </Button>
            </>
          ) : (
            <Button type="submit" disabled={isSubmitting} className="min-w-28">
              {isSubmitting ? "Salvando…" : "Salvar"}
            </Button>
          )}
        </DialogFooter>
      </form>
    </>
  );
}
