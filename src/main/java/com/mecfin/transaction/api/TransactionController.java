package com.mecfin.transaction.api;

import com.mecfin.transaction.application.TransactionService;
import com.mecfin.transaction.domain.Transaction;
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
@RequestMapping("/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse create(@Valid @RequestBody CreateTransactionRequest request) {
        Transaction transaction = transactionService.create(
                request.accountId(),
                request.categoryId(),
                request.type(),
                request.amount(),
                request.description(),
                request.transactionDate(),
                request.competenceMonth(),
                request.status());
        return TransactionResponse.from(transaction);
    }

    // competenceMonth recebido como String ("2026-08") e parseado manualmente em vez de deixar
    // o Spring MVC converter direto pra YearMonth - evita depender de registro implicito de
    // Converter<String,YearMonth>, que nao e garantido pelo ApplicationConversionService.
    @GetMapping
    public List<TransactionResponse> list(@RequestParam(required = false) String competenceMonth) {
        YearMonth month = parseCompetenceMonth(competenceMonth);
        return transactionService.list(month).stream().map(TransactionResponse::from).toList();
    }

    private YearMonth parseCompetenceMonth(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return YearMonth.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("competenceMonth inválido, use o formato yyyy-MM: " + value);
        }
    }

    @GetMapping("/{id}")
    public TransactionResponse get(@PathVariable UUID id) {
        return TransactionResponse.from(transactionService.get(id));
    }

    @PutMapping("/{id}")
    public TransactionResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateTransactionRequest request) {
        Transaction transaction = transactionService.update(
                id,
                request.categoryId(),
                request.type(),
                request.amount(),
                request.description(),
                request.transactionDate(),
                request.competenceMonth(),
                request.status());
        return TransactionResponse.from(transaction);
    }

    // Nunca hard-deleta (ver Transaction.cancel()) - o verbo HTTP continua DELETE porque, do
    // ponto de vista do cliente, a intencao e "remover este lancamento"; internamente vira
    // um cancelamento (estorno), preservando historico/relatorio.
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable UUID id) {
        transactionService.cancel(id);
    }
}
