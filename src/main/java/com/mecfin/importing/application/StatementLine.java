package com.mecfin.importing.application;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Uma linha de extrato já normalizada, independente do formato de origem. amount tem sinal:
 * negativo = saída (despesa), positivo = entrada (receita) — é como os bancos exportam.
 */
public record StatementLine(String externalId, LocalDate date, BigDecimal amount, String description) {
}
