import { cn } from "@/lib/utils";

interface StatTileProps {
  label: string;
  value: string;
  tone?: "default" | "positive" | "negative";
  className?: string;
}

/**
 * label (sentence case, sem dois-pontos) + value (semibold, figuras
 * proporcionais — nao tabular-nums, que e so pra colunas alinhadas de
 * numeros, nao um numero grande isolado).
 */
export function StatTile({ label, value, tone = "default", className }: StatTileProps) {
  return (
    <div className={cn("rounded-xl border border-border bg-card p-4", className)}>
      <div className="text-sm text-muted-foreground">{label}</div>
      <div
        className={cn(
          "mt-1 text-2xl font-semibold",
          tone === "positive" && "text-success",
          tone === "negative" && "text-destructive",
        )}
      >
        {value}
      </div>
    </div>
  );
}
