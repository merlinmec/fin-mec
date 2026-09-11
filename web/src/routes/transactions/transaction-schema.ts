import { z } from "zod";
import { ENTRY_TYPES } from "@/api/transactions";

const amount = z.number({ message: "Informe um valor válido" }).positive("O valor deve ser maior que zero");
const description = z.string().min(1, "Informe uma descrição").max(255, "Máximo de 255 caracteres");
const isoDate = z.string().min(1, "Informe a data");
const yearMonth = z.string().min(1, "Informe a competência");
const status = z.enum(["PENDING", "POSTED"], { message: "Selecione um status" });

/** Espelha com.mecfin.transaction.api.CreateTransactionRequest/UpdateTransactionRequest (mesmo shape). */
export const entrySchema = z.object({
  accountId: z.string().min(1, "Selecione uma conta"),
  categoryId: z.string().optional(),
  type: z.enum(ENTRY_TYPES),
  amount,
  description,
  transactionDate: isoDate,
  competenceMonth: yearMonth,
  status,
  // "" = sem recorrencia; RecurrenceRule real caso contrario.
  recurrenceRule: z.string(),
});

export type EntryFormValues = z.infer<typeof entrySchema>;

/** Espelha com.mecfin.transaction.api.CreateTransferRequest. */
export const transferSchema = z
  .object({
    sourceAccountId: z.string().min(1, "Selecione a conta de origem"),
    destinationAccountId: z.string().min(1, "Selecione a conta de destino"),
    amount,
    description,
    transactionDate: isoDate,
    competenceMonth: yearMonth,
    status,
  })
  .refine((v) => v.sourceAccountId !== v.destinationAccountId, {
    message: "A conta de origem e a de destino não podem ser a mesma",
    path: ["destinationAccountId"],
  });

export type TransferFormValues = z.infer<typeof transferSchema>;

/** Espelha com.mecfin.transaction.api.CreateInstallmentRequest. */
export const installmentSchema = z.object({
  accountId: z.string().min(1, "Selecione uma conta"),
  categoryId: z.string().optional(),
  type: z.enum(ENTRY_TYPES),
  amountPerInstallment: amount,
  description,
  firstTransactionDate: isoDate,
  firstCompetenceMonth: yearMonth,
  installments: z.number({ message: "Informe a quantidade de parcelas" }).int().min(2, "Mínimo de 2 parcelas"),
});

export type InstallmentFormValues = z.infer<typeof installmentSchema>;
