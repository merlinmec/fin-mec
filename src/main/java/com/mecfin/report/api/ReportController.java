package com.mecfin.report.api;

import com.mecfin.report.application.CashFlowReport;
import com.mecfin.report.application.CategoryReport;
import com.mecfin.report.application.ReportService;
import com.mecfin.transaction.domain.TransactionType;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Relatórios só-leitura. Os records de application já são o contrato de resposta (só tipos
 * simples, sem entidade dentro) - não há mapeamento a fazer, diferente dos módulos com escrita.
 * Período omitido = últimos 6 meses terminando no mês corrente.
 */
@RestController
@RequestMapping("/reports")
public class ReportController {

    private static final int DEFAULT_MONTHS = 6;

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/cash-flow")
    public CashFlowReport cashFlow(
            @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        YearMonth end = parse("to", to, YearMonth.now());
        return reportService.cashFlow(parse("from", from, end.minusMonths(DEFAULT_MONTHS - 1L)), end);
    }

    @GetMapping("/categories")
    public CategoryReport categories(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "EXPENSE") TransactionType type) {
        YearMonth end = parse("to", to, YearMonth.now());
        return reportService.byCategory(parse("from", from, end.minusMonths(DEFAULT_MONTHS - 1L)), end, type);
    }

    // Mesmo motivo de TransactionController/DashboardController para parsear YearMonth à mão.
    private static YearMonth parse(String name, String value, YearMonth fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return YearMonth.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(name + " inválido, use o formato yyyy-MM: " + value);
        }
    }
}
