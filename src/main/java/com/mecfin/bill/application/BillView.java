package com.mecfin.bill.application;

import com.mecfin.bill.domain.Bill;
import com.mecfin.bill.domain.BillStatus;
import java.time.LocalDate;

/**
 * Bill + status efetivo calculado na leitura (nunca persistido, ver {@link Bill}). Modelo de
 * leitura composto, não entidade JPA - mesmo padrão de {@code BudgetView}.
 */
public record BillView(Bill bill) {

    public BillStatus effectiveStatus() {
        if (bill.getStatus() == BillStatus.OPEN && bill.getDueDate().isBefore(LocalDate.now())) {
            return BillStatus.OVERDUE;
        }
        return bill.getStatus();
    }
}
