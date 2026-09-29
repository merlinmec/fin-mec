package com.mecfin.importing.application;

/**
 * Quais colunas do CSV são data, descrição e valor (índices a partir de 0). invertSign serve
 * para extrato que exporta despesa como número positivo (comum em fatura de cartão).
 */
public record CsvMapping(int dateColumn, int descriptionColumn, int amountColumn, boolean invertSign) {
}
