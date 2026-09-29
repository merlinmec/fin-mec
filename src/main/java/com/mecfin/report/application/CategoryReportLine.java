package com.mecfin.report.application;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

// categoryId null = lançamentos sem categoria. share = % do total do período; changePercent =
// variação contra o período anterior de mesmo tamanho (null quando o anterior é zero).
public record CategoryReportLine(
        UUID categoryId,
        String name,
        String color,
        String icon,
        BigDecimal total,
        BigDecimal share,
        BigDecimal previousTotal,
        BigDecimal changePercent,
        BigDecimal monthlyAverage,
        List<MonthAmount> monthly) {
}
