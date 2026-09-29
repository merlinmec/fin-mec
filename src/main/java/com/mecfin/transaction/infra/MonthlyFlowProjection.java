package com.mecfin.transaction.infra;

import java.math.BigDecimal;

// Projeção de TransactionRepository.sumSignedByTransactionMonth: fluxo líquido efetivado por
// mês-calendário da data do lançamento (base da evolução de saldo).
public interface MonthlyFlowProjection {

    Integer getYear();

    Integer getMonth();

    BigDecimal getTotal();
}
