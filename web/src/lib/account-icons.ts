import { Landmark, PiggyBank, TrendingUp, Wallet, type LucideIcon } from "lucide-react";
import type { AccountType } from "@/api/accounts";

/** Um ícone por tipo de conta — igual em espírito ao CATEGORY_ICONS, mas fixo por enum (não escolhido pelo usuário). */
export const ACCOUNT_TYPE_ICONS: Record<AccountType, LucideIcon> = {
  CHECKING: Landmark,
  SAVINGS: PiggyBank,
  WALLET: Wallet,
  INVESTMENT: TrendingUp,
};

/**
 * Mesma paleta curada das categorias (lib/category-icons.ts) — contas não
 * têm campo de cor no backend (não são personalizáveis como categoria), então
 * a cor do ícone circular é derivada de forma determinística do id da conta,
 * só pra dar a mesma "identidade visual" (círculo colorido) que o Organizze
 * usa pra distinguir contas de bancos diferentes, sem fingir detectar banco
 * real nenhum.
 */
const ACCOUNT_COLORS = [
  "#8B5CF6",
  "#F59E0B",
  "#3B82F6",
  "#10B981",
  "#EC4899",
  "#14B8A6",
  "#F97316",
  "#06B6D4",
  "#A855F7",
  "#DC2626",
] as const;

export function colorForAccount(id: string): string {
  let hash = 0;
  for (let i = 0; i < id.length; i++) {
    hash = (hash * 31 + id.charCodeAt(i)) | 0;
  }
  return ACCOUNT_COLORS[Math.abs(hash) % ACCOUNT_COLORS.length];
}
