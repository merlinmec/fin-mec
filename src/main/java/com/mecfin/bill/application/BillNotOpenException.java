package com.mecfin.bill.application;

import com.mecfin.bill.domain.BillStatus;
import com.mecfin.shared.exception.ConflictException;
import java.util.UUID;

// Editar, pagar ou cancelar só é permitido enquanto a conta a pagar está OPEN (ou OVERDUE, que
// é status efetivo de uma OPEN vencida - a mesma guarda cobre os dois). Uma vez PAID ou
// CANCELED, essas ações não fazem mais sentido - reusada pelas três mutações em BillService.
public class BillNotOpenException extends ConflictException {

    public BillNotOpenException(UUID id, BillStatus currentStatus) {
        super("Conta a pagar " + id + " não está aberta (status atual: " + currentStatus + ")");
    }
}
