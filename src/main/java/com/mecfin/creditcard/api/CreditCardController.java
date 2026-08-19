package com.mecfin.creditcard.api;

import com.mecfin.creditcard.application.CreditCardService;
import com.mecfin.creditcard.domain.CreditCard;
import com.mecfin.creditcard.domain.CreditCardCharge;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/credit-cards")
public class CreditCardController {

    private final CreditCardService creditCardService;

    public CreditCardController(CreditCardService creditCardService) {
        this.creditCardService = creditCardService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreditCardResponse create(@Valid @RequestBody CreateCreditCardRequest request) {
        CreditCard card = creditCardService.create(
                request.name(), request.creditLimit(), request.closingDay(), request.dueDay(),
                request.paymentAccountId());
        return CreditCardResponse.from(card);
    }

    @GetMapping
    public List<CreditCardResponse> list() {
        return creditCardService.list().stream().map(CreditCardResponse::from).toList();
    }

    @GetMapping("/{id}")
    public CreditCardResponse get(@PathVariable UUID id) {
        return CreditCardResponse.from(creditCardService.get(id));
    }

    @PutMapping("/{id}")
    public CreditCardResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateCreditCardRequest request) {
        CreditCard card = creditCardService.update(
                id, request.name(), request.creditLimit(), request.closingDay(), request.dueDay(),
                request.paymentAccountId(), request.archived());
        return CreditCardResponse.from(card);
    }

    // Nunca hard-deleta - vira soft delete (deleted_at), mesmo espírito de Account.delete: uma
    // fatura já emitida não pode perder a referência do cartão.
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        creditCardService.delete(id);
    }

    // Registra uma compra/estorno no cartão - a fatura é resolvida automaticamente a partir de
    // purchaseDate + closingDay do cartão (CreditCardService.resolveOrCreateInvoice).
    @PostMapping("/{id}/charges")
    @ResponseStatus(HttpStatus.CREATED)
    public List<CreditCardChargeResponse> registerCharge(
            @PathVariable UUID id, @Valid @RequestBody CreateCreditCardChargeRequest request) {
        List<CreditCardCharge> charges = creditCardService.registerCharge(
                id, request.categoryId(), request.type(), request.amount(), request.description(),
                request.purchaseDate(), request.installments());
        return charges.stream().map(CreditCardChargeResponse::from).toList();
    }

    @GetMapping("/{id}/invoices")
    public List<CreditCardInvoiceResponse> listInvoices(@PathVariable UUID id) {
        return creditCardService.listInvoices(id).stream().map(CreditCardInvoiceResponse::from).toList();
    }
}
