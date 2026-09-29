package com.mecfin.importing.api;

import com.mecfin.importing.domain.ImportBatch;
import com.mecfin.importing.domain.ImportFormat;
import java.time.Instant;
import java.util.UUID;

public record ImportBatchResponse(
        UUID id,
        UUID accountId,
        String fileName,
        ImportFormat format,
        int createdCount,
        int matchedCount,
        int skippedCount,
        Instant undoneAt,
        Instant createdAt) {

    public static ImportBatchResponse from(ImportBatch batch) {
        return new ImportBatchResponse(batch.getId(), batch.getAccountId(), batch.getFileName(), batch.getFormat(),
                batch.getCreatedCount(), batch.getMatchedCount(), batch.getSkippedCount(), batch.getUndoneAt(),
                batch.getCreatedAt());
    }
}
