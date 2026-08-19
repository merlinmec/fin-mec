package com.mecfin.bill.api;

import com.mecfin.bill.application.BillService;
import com.mecfin.bill.application.BillView;
import com.mecfin.bill.domain.BillStatus;
import jakarta.validation.Valid;
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
@RequestMapping("/bills")
public class BillController {

    private final BillService billService;

    public BillController(BillService billService) {
        this.billService = billService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BillResponse create(@Valid @RequestBody CreateBillRequest request) {
        BillView view = billService.create(
                request.description(), request.amount(), request.dueDate(), request.sourceAccountId(),
                request.categoryId(), request.recurrenceRule());
        return BillResponse.from(view);
    }

    @GetMapping
    public List<BillResponse> list(@RequestParam(required = false) BillStatus status) {
        return billService.list(status).stream().map(BillResponse::from).toList();
    }

    @GetMapping("/{id}")
    public BillResponse get(@PathVariable UUID id) {
        return BillResponse.from(billService.get(id));
    }

    @PutMapping("/{id}")
    public BillResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateBillRequest request) {
        BillView view = billService.update(
                id, request.description(), request.amount(), request.dueDate(), request.sourceAccountId(),
                request.categoryId(), request.recurrenceRule());
        return BillResponse.from(view);
    }

    // Baixa: registra o pagamento (cria a Transaction de verdade via BillService.pay).
    @PostMapping("/{id}/pay")
    public BillResponse pay(@PathVariable UUID id, @Valid @RequestBody PayBillRequest request) {
        BillView view = billService.pay(id, request.accountId(), request.paymentDate(), request.paidAmount());
        return BillResponse.from(view);
    }

    // Nunca hard-deleta - vira cancelamento (mesmo espírito do DELETE de Transaction), só
    // permitido enquanto a conta a pagar está OPEN.
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable UUID id) {
        billService.cancel(id);
    }
}
