import { z } from "zod";

/** Espelha com.mecfin.bill.api.CreateBillRequest/UpdateBillRequest. */
export const billSchema = z.object({
  description: z.string().min(1, "Informe uma descrição").max(255, "Máximo de 255 caracteres"),
  amount: z.number({ message: "Informe um valor válido" }).positive("O valor deve ser maior que zero"),
  dueDate: z.string().min(1, "Informe o vencimento"),
  sourceAccountId: z.string().optional(),
  categoryId: z.string().optional(),
  // "" = sem recorrencia; RecurrenceRule real caso contrario (mesmo padrao de entrySchema).
  recurrenceRule: z.string(),
});

export type BillFormValues = z.infer<typeof billSchema>;

/** Espelha com.mecfin.bill.api.PayBillRequest — accountId/paidAmount sao opcionais no backend, mas o form sempre pede os dois explicitamente. */
export const payBillSchema = z.object({
  accountId: z.string().min(1, "Selecione a conta de pagamento"),
  paymentDate: z.string().min(1, "Informe a data do pagamento"),
  paidAmount: z.number({ message: "Informe um valor válido" }).positive("O valor deve ser maior que zero"),
});

export type PayBillFormValues = z.infer<typeof payBillSchema>;
