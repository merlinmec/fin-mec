package com.mecfin.transaction.infra;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

// Projeção de TransactionRepository.sumByCategoryAndMonth (relatório por categoria). categoryId
// nulo = lançamentos sem categoria, agrupados num balde próprio.
public interface CategoryMonthTotalProjection {

    UUID getCategoryId();

    LocalDate getMonth();

    BigDecimal getTotal();
}
