package com.mecfin.bill.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

// accountId é opcional só quando o bill já tem sourceAccountId padrão (BillService.pay valida
// isso). paidAmount opcional - null usa o valor planejado do bill (pagamento sem juros/desconto).
public record PayBillRequest(UUID accountId, @NotNull LocalDate paymentDate, @Positive BigDecimal paidAmount) {
}
