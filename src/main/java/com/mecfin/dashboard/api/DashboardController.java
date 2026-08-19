package com.mecfin.dashboard.api;

import com.mecfin.dashboard.application.DashboardService;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    // month recebido como String ("2026-08") e parseado manualmente, mesmo motivo de
    // TransactionController/BudgetController (evita depender de conversor implicito do Spring
    // MVC pra YearMonth em query param). Omitido = mes corrente.
    @GetMapping
    public DashboardResponse summarize(@RequestParam(required = false) String month) {
        YearMonth referenceMonth = parseMonth(month);
        return DashboardResponse.from(dashboardService.summarize(referenceMonth));
    }

    private YearMonth parseMonth(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return YearMonth.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("month inválido, use o formato yyyy-MM: " + value);
        }
    }
}
