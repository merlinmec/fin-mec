import { z } from "zod";

/** Espelha com.mecfin.identity.api.LoginRequest (so formato; a checagem de credencial e do backend). */
export const loginSchema = z.object({
  email: z.string().min(1, "Informe o e-mail").email("E-mail inválido"),
  password: z.string().min(1, "Informe a senha"),
});

export type LoginFormValues = z.infer<typeof loginSchema>;
