import { ChevronLeft, ChevronRight } from "lucide-react";
import { Button } from "@/components/ui/button";
import { formatYearMonth, shiftYearMonth } from "@/lib/dates";

interface MonthSelectorProps {
  /** yyyy-MM. */
  value: string;
  onChange: (next: string) => void;
}

/**
 * Introduzido na FE-4 (Lançamentos) como estado local da pagina; promovido
 * a componente compartilhado na FE-5 (Orçamento), que virou o segundo
 * consumidor identico. O plano previa isso ir pra topbar/Dashboard mais pra
 * frente (FE-8) — este componente ja fica pronto pra esse passo, sem
 * mudanca de API.
 */
export function MonthSelector({ value, onChange }: MonthSelectorProps) {
  return (
    <div className="flex items-center gap-1">
      <Button variant="ghost" size="icon" onClick={() => onChange(shiftYearMonth(value, -1))} aria-label="Mês anterior">
        <ChevronLeft className="size-4" />
      </Button>
      <span className="min-w-40 text-center text-sm font-medium">{formatYearMonth(value)}</span>
      <Button variant="ghost" size="icon" onClick={() => onChange(shiftYearMonth(value, 1))} aria-label="Próximo mês">
        <ChevronRight className="size-4" />
      </Button>
    </div>
  );
}
