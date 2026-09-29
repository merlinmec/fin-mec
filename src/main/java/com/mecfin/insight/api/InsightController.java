package com.mecfin.insight.api;

import com.mecfin.insight.application.BalanceForecast;
import com.mecfin.insight.application.ForecastService;
import com.mecfin.insight.application.MonthlySummary;
import com.mecfin.insight.application.MonthlySummaryService;
import java.time.YearMonth;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/insights")
public class InsightController {

    private final ForecastService forecastService;
    private final MonthlySummaryService monthlySummaryService;

    public InsightController(ForecastService forecastService, MonthlySummaryService monthlySummaryService) {
        this.forecastService = forecastService;
        this.monthlySummaryService = monthlySummaryService;
    }

    @GetMapping("/forecast")
    public BalanceForecast forecast(@RequestParam(defaultValue = "90") int days) {
        return forecastService.forecast(days);
    }

    @GetMapping("/monthly-summary")
    public MonthlySummary monthlySummary(@RequestParam(required = false) YearMonth month) {
        return monthlySummaryService.summarize(month);
    }
}
