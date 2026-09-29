package com.mecfin.importing.application;

/**
 * O que a pré-visualização concluiu sobre cada linha do extrato:
 * <ul>
 *   <li>NEW — lançamento novo;</li>
 *   <li>ALREADY_IMPORTED — esta linha exata já veio de uma importação anterior (mesmo ID no
 *       extrato); não pode ser importada de novo;</li>
 *   <li>POSSIBLE_DUPLICATE — já existe um lançamento manual na conta com a mesma data e o mesmo
 *       valor; por padrão fica de fora, mas o usuário pode incluir (duas compras iguais no dia
 *       existem);</li>
 *   <li>MATCHES_PENDING — casa com um lançamento previsto (ex.: ocorrência de um fixo): importar
 *       efetiva o previsto em vez de criar outro.</li>
 * </ul>
 */
public enum PreviewStatus {
    NEW,
    ALREADY_IMPORTED,
    POSSIBLE_DUPLICATE,
    MATCHES_PENDING
}
