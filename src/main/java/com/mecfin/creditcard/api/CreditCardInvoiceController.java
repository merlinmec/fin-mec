package com.mecfin.creditcard.api;

import com.mecfin.creditcard.application.CreditCardService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/credit-card-invoices")
public class CreditCardInvoiceController {

    private final CreditCardService creditCardService;

    public CreditCardInvoiceController(CreditCardService creditCardService) {
        this.creditCardService = creditCardService;
    }

    @GetMapping("/{id}")
    public CreditCardInvoiceResponse get(@PathVariable UUID id) {
        return CreditCardInvoiceResponse.from(creditCardService.getInvoice(id));
    }

    // Baixa: registra o pagamento como uma Transaction real (CreditCardService.payInvoice).
    @PostMapping("/{id}/pay")
    public CreditCardInvoiceResponse pay(@PathVariable UUID id, @Valid @RequestBody PayCreditCardInvoiceRequest request) {
        return CreditCardInvoiceResponse.from(
                creditCardService.payInvoice(id, request.accountId(), request.paymentDate(), request.paidAmount()));
    }

    // Corrige uma cobrança lançada errada - só permitido enquanto a fatura ainda está OPEN
    // (efetivo), nunca depois de fechada/paga.
    @DeleteMapping("/{id}/charges/{chargeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCharge(@PathVariable UUID id, @PathVariable UUID chargeId) {
        creditCardService.deleteCharge(id, chargeId);
    }
}
