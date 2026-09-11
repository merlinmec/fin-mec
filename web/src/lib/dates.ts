/**
 * Helpers de data do fin-mec. transactionDate (LocalDate) e competenceMonth
 * (YearMonth) chegam da API como string ISO (yyyy-MM-dd / yyyy-MM) — os
 * mesmos formatos que <input type="date"> e <input type="month"> usam como
 * value, entao os forms nao precisam converter nada pra editar.
 */

export function todayIso(): string {
  return toIsoDate(new Date());
}

function toIsoDate(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

export function currentYearMonth(): string {
  return todayIso().slice(0, 7);
}

/** yyyy-MM-dd -> dd/mm/aaaa. */
export function formatDate(iso: string): string {
  const [y, m, d] = iso.split("-");
  return `${d}/${m}/${y}`;
}

/** yyyy-MM -> "setembro de 2026". */
export function formatYearMonth(yearMonth: string): string {
  const [y, m] = yearMonth.split("-").map(Number);
  const label = new Date(y, m - 1, 1).toLocaleDateString("pt-BR", { month: "long", year: "numeric" });
  return label.charAt(0).toUpperCase() + label.slice(1);
}

/** yyyy-MM + N meses (N pode ser negativo) -> yyyy-MM. */
export function shiftYearMonth(yearMonth: string, deltaMonths: number): string {
  const [y, m] = yearMonth.split("-").map(Number);
  const date = new Date(y, m - 1 + deltaMonths, 1);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}`;
}

/** yyyy-MM-dd -> yyyy-MM (deriva a competencia a partir da data do lancamento). */
export function yearMonthOf(isoDate: string): string {
  return isoDate.slice(0, 7);
}
