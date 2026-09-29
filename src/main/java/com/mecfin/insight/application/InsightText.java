package com.mecfin.insight.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;

/** Formatação das frases geradas (resumo e alertas): sempre pt-BR, independente do servidor. */
final class InsightText {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private InsightText() {
    }

    // O formato pt-BR do JDK separa "R$" do número com espaço não separável (U+00A0/U+202F):
    // em texto gravado e copiado (notificação, e-mail) vira lixo em alguns clientes.
    static String money(BigDecimal value) {
        return NumberFormat.getCurrencyInstance(PT_BR).format(value.setScale(2, RoundingMode.HALF_EVEN))
                .replace(' ', ' ')
                .replace(' ', ' ');
    }

    static String percent(BigDecimal value) {
        return value.abs().setScale(0, RoundingMode.HALF_EVEN).toPlainString() + "%";
    }

    static String monthName(YearMonth month) {
        return month.getMonth().getDisplayName(TextStyle.FULL, PT_BR);
    }
}
