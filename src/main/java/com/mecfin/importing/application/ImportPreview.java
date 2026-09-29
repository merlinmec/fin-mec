package com.mecfin.importing.application;

import com.mecfin.importing.domain.ImportFormat;
import java.util.List;

/** columns/mapping só existem para CSV (a UI deixa o usuário trocar as colunas e reenviar). */
public record ImportPreview(
        ImportFormat format,
        String fileName,
        List<String> columns,
        CsvMapping mapping,
        List<PreviewRow> rows,
        long newCount,
        long alreadyImportedCount,
        long possibleDuplicateCount,
        long matchCount) {
}
