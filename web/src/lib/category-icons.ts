import {
  Baby,
  Banknote,
  BookOpen,
  Briefcase,
  Car,
  Coffee,
  CreditCard,
  Dog,
  Dumbbell,
  Fuel,
  Gamepad2,
  Gift,
  GraduationCap,
  HandCoins,
  HeartPulse,
  Home,
  Landmark,
  Phone,
  PiggyBank,
  Plane,
  Popcorn,
  Receipt,
  ShoppingCart,
  Shirt,
  Tag,
  TrendingUp,
  Tv,
  Utensils,
  Wallet,
  Wrench,
  type LucideIcon,
} from "lucide-react";

/**
 * Conjunto curado de icones pra categoria — nao o mapa dinamico inteiro do
 * lucide-react (icons/dynamicIconImports), que traria centenas de icones pro
 * bundle so pra resolver por nome. As 6 categorias padrao do sistema
 * (V5__seed_default_categories.sql) usam nomes daqui; o restante cobre os
 * casos mais comuns de categoria financeira pessoal. Nomes em kebab-case pra
 * bater com o que o backend guarda em Category.icon (string livre, sem
 * validacao de enum — o icon picker e so uma conveniencia de UI).
 */
export const CATEGORY_ICONS: Record<string, LucideIcon> = {
  home: Home,
  utensils: Utensils,
  car: Car,
  "heart-pulse": HeartPulse,
  "graduation-cap": GraduationCap,
  popcorn: Popcorn,
  "shopping-cart": ShoppingCart,
  plane: Plane,
  dumbbell: Dumbbell,
  gift: Gift,
  briefcase: Briefcase,
  "piggy-bank": PiggyBank,
  wallet: Wallet,
  coffee: Coffee,
  shirt: Shirt,
  dog: Dog,
  "gamepad-2": Gamepad2,
  wrench: Wrench,
  phone: Phone,
  tv: Tv,
  "book-open": BookOpen,
  baby: Baby,
  fuel: Fuel,
  receipt: Receipt,
  banknote: Banknote,
  "credit-card": CreditCard,
  "trending-up": TrendingUp,
  landmark: Landmark,
  "hand-coins": HandCoins,
};

export const CATEGORY_ICON_NAMES = Object.keys(CATEGORY_ICONS);

/** Usado quando a categoria nao tem icon (opcional no backend) ou tem um nome que nao esta no conjunto curado. */
export const DEFAULT_CATEGORY_ICON: LucideIcon = Tag;

export function getCategoryIcon(icon: string | null | undefined): LucideIcon {
  if (icon && icon in CATEGORY_ICONS) {
    return CATEGORY_ICONS[icon];
  }
  return DEFAULT_CATEGORY_ICON;
}

/**
 * Paleta curada pro color picker — os 6 tons ja usados nas categorias padrao
 * do sistema (mesma paleta visual) mais alguns complementares. Regex do
 * backend exige exatamente #RRGGBB (CreateCategoryRequest/UpdateCategoryRequest).
 */
export const CATEGORY_COLORS = [
  "#8B5CF6",
  "#F59E0B",
  "#3B82F6",
  "#EF4444",
  "#10B981",
  "#EC4899",
  "#6B7280",
  "#14B8A6",
  "#F97316",
  "#84CC16",
  "#06B6D4",
  "#A855F7",
] as const;
