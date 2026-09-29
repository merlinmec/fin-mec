package com.mecfin.insight.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Curva de saldo dia a dia (Fase 19). Parte do saldo disponível de hoje e aplica, na data de
 * cada um, o que já se sabe que vai acontecer: previstos (fixos, parcelas), lançamentos com data
 * futura, contas a pagar em aberto e faturas de cartão a pagar. O que está atrasado entra no dia
 * de hoje — ainda vai sair do bolso.
 *
 * <p>{@code firstNegativeDate} é o dado mais útil da tela: "seu saldo fica negativo em 12/11".
 */
public record BalanceForecast(
        LocalDate from,
        LocalDate to,
        BigDecimal startBalance,
        BigDecimal endBalance,
        BigDecimal lowestBalance,
        LocalDate lowestDate,
        LocalDate firstNegativeDate,
        BigDecimal totalIncome,
        BigDecimal totalExpense,
        List<Point> points) {

    public enum EventKind {
        TRANSACTION,
        BILL,
        CREDIT_CARD_INVOICE
    }

    /** amount com sinal: positivo entra, negativo sai. */
    public record Event(LocalDate date, EventKind kind, String description, BigDecimal amount, boolean overdue) {
    }

    public record Point(LocalDate date, BigDecimal balance, BigDecimal income, BigDecimal expense,
            List<Event> events) {
    }
}
