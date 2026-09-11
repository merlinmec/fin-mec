import { z } from "zod";

/** Espelha com.mecfin.creditcard.api.PayCreditCardInvoiceRequest — accountId/paidAmount sao opcionais no backend, mas o form sempre pede os dois explicitamente (mesmo padrao de payBillSchema). */
export const payInvoiceSchema = z.object({
  accountId: z.string().min(1, "Selecione a conta de pagamento"),
  paymentDate: z.string().min(1, "Informe a data do pagamento"),
  paidAmount: z.number({ message: "Informe um valor válido" }).positive("O valor deve ser maior que zero"),
});

export type PayInvoiceFormValues = z.infer<typeof payInvoiceSchema>;
