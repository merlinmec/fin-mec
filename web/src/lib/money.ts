/**
 * Formatacao de dinheiro do fin-mec. Moeda fixa em BRL (mesma decisao do
 * backend — Account.currency e sempre "BRL", ver com.mecfin.account.domain.Account).
 * Espelha o valor de com.mecfin.shared.domain.Money na apresentacao, sem
 * reimplementar a logica de arredondamento/precisao dele — os valores ja
 * chegam prontos da API, aqui e so exibicao.
 */
const formatter = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });

export function formatMoney(value: number): string {
  return formatter.format(value);
}
