import { cn } from "@/lib/utils";

interface ProgressProps {
  /** 0-100+ (pode passar de 100 — a barra satura em 100%, quem le o numero ve o estouro). */
  value: number;
  className?: string;
  indicatorClassName?: string;
}

/**
 * Div-based, nao Radix — uma barra puramente visual (sem drag, sem estado
 * assincrono) nao precisa da primitiva completa; role="progressbar" manual
 * cobre a acessibilidade que importa aqui.
 */
export function Progress({ value, className, indicatorClassName }: ProgressProps) {
  const clamped = Math.min(100, Math.max(0, value));
  return (
    <div
      role="progressbar"
      aria-valuenow={Math.round(value)}
      aria-valuemin={0}
      aria-valuemax={100}
      className={cn("h-2 w-full overflow-hidden rounded-full bg-muted", className)}
    >
      <div
        className={cn("h-full rounded-full transition-[width]", indicatorClassName)}
        style={{ width: `${clamped}%` }}
      />
    </div>
  );
}
