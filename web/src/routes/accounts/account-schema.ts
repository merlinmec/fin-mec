import { z } from "zod";
import { ACCOUNT_TYPES } from "@/api/accounts";

const name = z.string().min(1, "Informe o nome").max(120, "Máximo de 120 caracteres");
const type = z.enum(ACCOUNT_TYPES, { message: "Selecione um tipo" });

/** Espelha com.mecfin.account.api.CreateAccountRequest. */
export const createAccountSchema = z.object({
  name,
  type,
  // z.coerce.number() faria o resolver do react-hook-form inferir o tipo de
  // ENTRADA do campo (antes da coercao) como unknown, descasando do tipo de
  // SAIDA usado em useForm<CreateAccountFormValues>. Em vez disso, o campo
  // numerico usa valueAsNumber no register() (ver AccountFormDialog) — o RHF
  // ja entrega number pronto, e o schema so valida.
  initialBalance: z.number({ message: "Informe um valor válido" }),
});

export type CreateAccountFormValues = z.infer<typeof createAccountSchema>;

/** Espelha com.mecfin.account.api.UpdateAccountRequest — sem initialBalance (nao editavel). */
export const updateAccountSchema = z.object({
  name,
  type,
  archived: z.boolean(),
});

export type UpdateAccountFormValues = z.infer<typeof updateAccountSchema>;
