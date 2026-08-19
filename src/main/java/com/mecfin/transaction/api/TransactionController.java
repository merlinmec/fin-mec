package com.mecfin.transaction.api;

import com.mecfin.shared.web.PagedResponse;
import com.mecfin.transaction.application.TransactionService;
import com.mecfin.transaction.domain.Transaction;
import com.mecfin.transaction.domain.TransactionStatus;
import com.mecfin.transaction.domain.TransactionType;
import jakarta.validation.Valid;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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

    private static final int MAX_PAGE_SIZE = 100;

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
                request.status(),
                request.recurrenceRule());
        return TransactionResponse.from(transaction);
    }

    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    public List<TransactionResponse> createTransfer(@Valid @RequestBody CreateTransferRequest request) {
        return transactionService.createTransfer(
                        request.sourceAccountId(),
                        request.destinationAccountId(),
                        request.amount(),
                        request.description(),
                        request.transactionDate(),
                        request.competenceMonth(),
                        request.status())
                .stream()
                .map(TransactionResponse::from)
                .toList();
    }

    @PostMapping("/installments")
    @ResponseStatus(HttpStatus.CREATED)
    public List<TransactionResponse> createInstallments(@Valid @RequestBody CreateInstallmentRequest request) {
        return transactionService.createInstallments(
                        request.accountId(),
                        request.categoryId(),
                        request.type(),
                        request.amountPerInstallment(),
                        request.description(),
                        request.firstTransactionDate(),
                        request.firstCompetenceMonth(),
                        request.installments())
                .stream()
                .map(TransactionResponse::from)
                .toList();
    }

    // competenceMonth recebido como String ("2026-08") e parseado manualmente em vez de deixar
    // o Spring MVC converter direto pra YearMonth - evita depender de registro implicito de
    // Converter<String,YearMonth>, que nao e garantido pelo ApplicationConversionService.
    // accountId/categoryId/type/status são opcionais e combináveis; Spring MVC já converte
    // TransactionType/TransactionStatus de query param nativamente (enum coberto pelo
    // conversion service padrão, diferente de YearMonth).
    @GetMapping
    public PagedResponse<TransactionResponse> list(
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(required = false) String competenceMonth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        YearMonth month = parseCompetenceMonth(competenceMonth);
        Page<Transaction> result = transactionService.search(
                accountId, categoryId, type, status, month, PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE)));
        return PagedResponse.from(result.map(TransactionResponse::from));
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
                request.status(),
                request.recurrenceRule());
        return TransactionResponse.from(transaction);
    }

    // Nunca hard-deleta (ver Transaction.cancel()) - o verbo HTTP continua DELETE porque, do
    // ponto de vista do cliente, a intencao e "remover este lancamento"; internamente vira
    // um cancelamento (estorno), preservando historico/relatorio. Cascade para a perna
    // pareada quando é transferência (TransactionService.cancel).
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable UUID id) {
        transactionService.cancel(id);
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
}
