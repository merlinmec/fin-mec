/** Formatações curtas dos eixos dos gráficos (o valor completo vai no tooltip e na tabela). */

const compact = new Intl.NumberFormat("pt-BR", { notation: "compact", maximumFractionDigits: 1 });

/** 12500 -> "12,5 mil". */
export function formatCompactMoney(value: number): string {
  return compact.format(value);
}

/** yyyy-MM -> "set/26". */
export function formatShortMonth(yearMonth: string): string {
  const [y, m] = yearMonth.split("-").map(Number);
  const month = new Date(y, m - 1, 1)
    .toLocaleDateString("pt-BR", { month: "short" })
    .replace(".", "");
  return `${month}/${String(y).slice(2)}`;
}

/** yyyy-MM-dd -> "12/11". */
export function formatDayMonth(iso: string): string {
  const [, m, d] = iso.split("-");
  return `${d}/${m}`;
}
