package com.mecfin.creditcard.api;

import com.mecfin.transaction.domain.TransactionType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

// amount é o valor de CADA parcela quando installments é informado (não o total) - mesma
// convenção de CreateInstallmentRequest.amountPerInstallment, evita ambiguidade de resto de
// divisão. installments omitido (ou < 2) vira uma cobrança avulsa.
public record CreateCreditCardChargeRequest(
        @NotBlank @Size(max = 255) String description,
        @NotNull @Positive BigDecimal amount,
        @NotNull TransactionType type,
        UUID categoryId,
        @NotNull LocalDate purchaseDate,
        @Min(2) Integer installments) {
}
