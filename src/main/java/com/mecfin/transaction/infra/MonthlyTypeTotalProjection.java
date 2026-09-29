package com.mecfin.transaction.infra;

import com.mecfin.transaction.domain.TransactionStatus;
import com.mecfin.transaction.domain.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;

// Projeção de TransactionRepository.sumByMonthTypeAndStatus (relatório de fluxo de caixa).
public interface MonthlyTypeTotalProjection {

    LocalDate getMonth();

    TransactionType getType();

    TransactionStatus getStatus();

    BigDecimal getTotal();
}
