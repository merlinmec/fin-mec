package com.mecfin.report.application;

import com.mecfin.transaction.domain.TransactionType;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

public record CategoryReport(
        YearMonth from,
        YearMonth to,
        TransactionType type,
        BigDecimal total,
        BigDecimal previousTotal,
        BigDecimal changePercent,
        List<CategoryReportLine> categories) {
}
