import { z } from "zod";

const name = z.string().min(1, "Informe o nome").max(120, "Máximo de 120 caracteres");
const creditLimit = z.number({ message: "Informe um valor válido" }).positive("O limite deve ser maior que zero");
const closingDay = z.number({ message: "Informe o dia de fechamento" }).int().min(1, "Entre 1 e 31").max(31, "Entre 1 e 31");
const dueDay = z.number({ message: "Informe o dia de vencimento" }).int().min(1, "Entre 1 e 31").max(31, "Entre 1 e 31");

/** Espelha com.mecfin.creditcard.api.CreateCreditCardRequest. */
export const createCreditCardSchema = z.object({
  name,
  creditLimit,
  closingDay,
  dueDay,
  paymentAccountId: z.string().optional(),
});

export type CreateCreditCardFormValues = z.infer<typeof createCreditCardSchema>;

/** Espelha com.mecfin.creditcard.api.UpdateCreditCardRequest. */
export const updateCreditCardSchema = createCreditCardSchema.extend({ archived: z.boolean() });

export type UpdateCreditCardFormValues = z.infer<typeof updateCreditCardSchema>;
