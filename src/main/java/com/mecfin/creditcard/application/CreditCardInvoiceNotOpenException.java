package com.mecfin.creditcard.application;

import com.mecfin.creditcard.domain.CreditCardInvoiceStatus;
import com.mecfin.shared.exception.ConflictException;
import java.util.UUID;

// Registrar/apagar cobrança só são permitidos com a fatura no status EFETIVO OPEN (antes do
// fechamento) - ver CreditCardService#requireOpen. Pagar é mais permissivo: funciona com OPEN
// ou CLOSED (o caso normal - fatura fechou, hora de pagar), só PAID é bloqueado - ver
// CreditCardService#requireNotPaid. Mesmo espírito de BillNotOpenException/Bill.isOpen(), que
// já permitia pagar uma Bill OVERDUE.
public class CreditCardInvoiceNotOpenException extends ConflictException {

    public CreditCardInvoiceNotOpenException(UUID id, CreditCardInvoiceStatus effectiveStatus) {
        super("Fatura " + id + " não está aberta (status atual: " + effectiveStatus + ")");
    }
}
