package com.mecfin.transaction.api;

import com.mecfin.transaction.application.RecurringSeriesService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lançamentos fixos (séries de recorrência). Criar/editar uma série acontece via
 * /transactions (recurrenceRule + scope); aqui ficam só a visão consolidada e o "parar de
 * repetir" - equivalente a excluir "esta e as próximas" a partir de hoje.
 */
@RestController
@RequestMapping("/recurring-series")
public class RecurringSeriesController {

    private final RecurringSeriesService recurringSeriesService;

    public RecurringSeriesController(RecurringSeriesService recurringSeriesService) {
        this.recurringSeriesService = recurringSeriesService;
    }

    @GetMapping
    public List<RecurringSeriesResponse> list() {
        return recurringSeriesService.list().stream().map(RecurringSeriesResponse::from).toList();
    }

    @PostMapping("/{id}/stop")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void stop(@PathVariable UUID id) {
        recurringSeriesService.stop(id);
    }
}
