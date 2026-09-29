package com.mecfin.report.application;

import java.math.BigDecimal;
import java.time.YearMonth;

public record MonthAmount(YearMonth month, BigDecimal amount) {
}
