import { getCategoryIcon } from "@/lib/category-icons";
import { cn } from "@/lib/utils";

interface CategoryIconProps {
  icon: string | null | undefined;
  color: string | null | undefined;
  className?: string;
}

/** Badge redondo com a cor da categoria e o icone (lucide) dentro, branco por cima. */
export function CategoryIcon({ icon, color, className }: CategoryIconProps) {
  const Icon = getCategoryIcon(icon);
  return (
    <span
      className={cn("inline-flex size-6 shrink-0 items-center justify-center rounded-full text-white", className)}
      style={{ backgroundColor: color ?? "var(--color-muted-foreground)" }}
    >
      <Icon className="size-3.5" />
    </span>
  );
}
