package com.mecfin.importing.api;

import com.mecfin.importing.application.CommitRow;
import com.mecfin.importing.domain.ImportFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CommitImportRequest(
        @NotNull UUID accountId,
        @Size(max = 255) String fileName,
        @NotNull ImportFormat format,
        @NotEmpty @Size(max = 2000) List<@Valid Row> rows) {

    /** amount com sinal: negativo = saída (despesa), positivo = entrada (receita). */
    public record Row(
            @NotBlank @Size(max = 120) String externalId,
            @NotNull LocalDate date,
            @NotBlank @Size(max = 255) String description,
            @NotNull BigDecimal amount,
            @NotNull CommitRow.Action action,
            UUID categoryId,
            @Size(max = 10) List<UUID> tagIds,
            UUID matchTransactionId) {

        CommitRow toCommitRow() {
            return new CommitRow(externalId, date, description, amount, action, categoryId, tagIds, matchTransactionId);
        }
    }
}
