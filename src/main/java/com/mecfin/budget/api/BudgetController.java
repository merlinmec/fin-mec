package com.mecfin.budget.api;

import com.mecfin.budget.application.BudgetService;
import com.mecfin.budget.application.BudgetView;
import jakarta.validation.Valid;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/budgets")
public class BudgetController {

    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BudgetResponse create(@Valid @RequestBody CreateBudgetRequest request) {
        BudgetView view = budgetService.create(request.categoryId(), request.referenceMonth(), request.amount());
        return BudgetResponse.from(view);
    }

    // referenceMonth recebido como String ("2026-08") e parseado manualmente, mesmo motivo de
    // TransactionController.parseCompetenceMonth (evita depender de conversor implicito do
    // Spring MVC para YearMonth em query param).
    @GetMapping
    public List<BudgetResponse> list(@RequestParam(required = false) String referenceMonth) {
        YearMonth month = parseReferenceMonth(referenceMonth);
        return budgetService.list(month).stream().map(BudgetResponse::from).toList();
    }

    @GetMapping("/{id}")
    public BudgetResponse get(@PathVariable UUID id) {
        return BudgetResponse.from(budgetService.get(id));
    }

    @PutMapping("/{id}")
    public BudgetResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateBudgetRequest request) {
        BudgetView view = budgetService.update(id, request.amount());
        return BudgetResponse.from(view);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        budgetService.delete(id);
    }

    private YearMonth parseReferenceMonth(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return YearMonth.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("referenceMonth inválido, use o formato yyyy-MM: " + value);
        }
    }
}
