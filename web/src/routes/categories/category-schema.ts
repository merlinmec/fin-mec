import { z } from "zod";
import { CATEGORY_TYPES } from "@/api/categories";
import { CATEGORY_ICON_NAMES } from "@/lib/category-icons";

// color/icon sao opcionais no backend (Category.color/icon aceitam null), mas
// o form exige os dois — toda categoria criada pela UI fica visualmente
// distinta desde o começo, sem depender de editar depois.
export const categoryFormSchema = z.object({
  name: z.string().min(1, "Informe o nome").max(120, "Máximo de 120 caracteres"),
  type: z.enum(CATEGORY_TYPES, { message: "Selecione um tipo" }),
  color: z.string().regex(/^#[0-9A-Fa-f]{6}$/, "Cor deve estar no formato #RRGGBB"),
  icon: z.enum(CATEGORY_ICON_NAMES as [string, ...string[]], { message: "Selecione um ícone" }),
});

export type CategoryFormValues = z.infer<typeof categoryFormSchema>;
