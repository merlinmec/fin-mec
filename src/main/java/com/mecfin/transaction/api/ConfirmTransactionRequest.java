package com.mecfin.transaction.api;

import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

// Os dois campos são opcionais: null = usa o valor/data previstos no lançamento pendente.
public record ConfirmTransactionRequest(@Positive BigDecimal amount, LocalDate transactionDate) {
}
