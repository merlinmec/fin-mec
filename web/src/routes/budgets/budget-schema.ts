import { z } from "zod";

const amount = z.number({ message: "Informe um valor válido" }).positive("O valor deve ser maior que zero");

/** Espelha com.mecfin.budget.api.CreateBudgetRequest. */
export const createBudgetSchema = z.object({
  categoryId: z.string().min(1, "Selecione uma categoria"),
  referenceMonth: z.string().min(1, "Selecione o mês"),
  amount,
});

export type CreateBudgetFormValues = z.infer<typeof createBudgetSchema>;

/** Espelha com.mecfin.budget.api.UpdateBudgetRequest — categoria e mes sao imutaveis apos criado. */
export const updateBudgetSchema = z.object({ amount });

export type UpdateBudgetFormValues = z.infer<typeof updateBudgetSchema>;
