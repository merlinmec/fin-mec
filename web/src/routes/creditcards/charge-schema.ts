import { z } from "zod";
import { ENTRY_TYPES } from "@/api/transactions";

/** Espelha com.mecfin.creditcard.api.CreateCreditCardChargeRequest — installments omitido/undefined vira cobranca avulsa. */
export const chargeSchema = z.object({
  description: z.string().min(1, "Informe uma descrição").max(255, "Máximo de 255 caracteres"),
  amount: z.number({ message: "Informe um valor válido" }).positive("O valor deve ser maior que zero"),
  type: z.enum(ENTRY_TYPES),
  categoryId: z.string().optional(),
  purchaseDate: z.string().min(1, "Informe a data da compra"),
  installments: z.number().int().min(2, "Mínimo de 2 parcelas").optional(),
});

export type ChargeFormValues = z.infer<typeof chargeSchema>;
