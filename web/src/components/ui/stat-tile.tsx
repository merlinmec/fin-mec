import { Eye, EyeOff } from "lucide-react";
import { cn } from "@/lib/utils";

interface StatTileProps {
  label: string;
  value: string;
  tone?: "default" | "positive" | "negative";
  className?: string;
  /** Barra de destaque verde à esquerda — reservado pro(s) stat(s) "lead" do card grid (padrão Organizze: "Saldo geral"). */
  accent?: boolean;
  /** Quando presente, mostra o botão de olho e usa `hidden` pra mascarar `value`. */
  hidden?: boolean;
  onToggleHidden?: () => void;
}

/**
 * label (sentence case, sem dois-pontos) + value (semibold, figuras
 * proporcionais — nao tabular-nums, que e so pra colunas alinhadas de
 * numeros, nao um numero grande isolado).
 */
export function StatTile({
  label,
  value,
  tone = "default",
  className,
  accent = false,
  hidden,
  onToggleHidden,
}: StatTileProps) {
  const maskable = onToggleHidden !== undefined;

  return (
    <div
      className={cn(
        "rounded-2xl bg-card p-4 shadow-sm",
        accent ? "border-l-4 border-primary" : "border border-border/60",
        className,
      )}
    >
      <div className="flex items-center justify-between gap-2">
        <div className="text-sm text-muted-foreground">{label}</div>
        {maskable && (
          <button
            type="button"
            onClick={onToggleHidden}
            aria-label={hidden ? "Mostrar valor" : "Ocultar valor"}
            className="text-muted-foreground/70 transition-colors hover:text-foreground"
          >
            {hidden ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
          </button>
        )}
      </div>
      <div
        className={cn(
          "mt-1 text-2xl font-semibold",
          tone === "positive" && "text-success",
          tone === "negative" && "text-destructive",
        )}
      >
        {maskable && hidden ? "••••••" : value}
      </div>
    </div>
  );
}
