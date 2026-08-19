package com.mecfin.creditcard.application;

import com.mecfin.creditcard.domain.CreditCardInvoiceStatus;
import com.mecfin.shared.exception.ConflictException;
import java.util.UUID;

// Registrar cobrança, apagar cobrança e pagar só são permitidos enquanto a fatura está com
// status efetivo OPEN - uma vez CLOSED (fechada, aguardando pagamento) ou PAID, essas ações não
// fazem mais sentido. Mesmo espírito de BillNotOpenException.
public class CreditCardInvoiceNotOpenException extends ConflictException {

    public CreditCardInvoiceNotOpenException(UUID id, CreditCardInvoiceStatus effectiveStatus) {
        super("Fatura " + id + " não está aberta (status atual: " + effectiveStatus + ")");
    }
}
