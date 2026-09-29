import { useQuery } from "@tanstack/react-query";
import { getMonthlySummary } from "@/api/insights";
import { formatYearMonth } from "@/lib/dates";

export function useMonthlySummary() {
  return useQuery({
    queryKey: ["insights", "monthly-summary"],
    queryFn: () => getMonthlySummary(),
  });
}

/** Título do card: o mês resumido é sempre o último fechado (definido pelo backend). */
export function monthlySummaryTitle(month: string | undefined): string {
  return month
    ? `Resumo de ${formatYearMonth(month).split(" de ")[0].toLowerCase()}`
    : "Resumo do mês";
}
