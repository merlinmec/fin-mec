import { z } from "zod";

/** Espelha com.mecfin.identity.api.RegisterRequest (senha: 10-72 caracteres). */
export const registerSchema = z.object({
  email: z.string().min(1, "Informe o e-mail").email("E-mail inválido"),
  password: z
    .string()
    .min(10, "A senha precisa ter no mínimo 10 caracteres")
    .max(72, "A senha pode ter no máximo 72 caracteres"),
});

export type RegisterFormValues = z.infer<typeof registerSchema>;
