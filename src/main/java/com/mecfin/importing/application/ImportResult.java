package com.mecfin.importing.application;

import java.util.UUID;

public record ImportResult(UUID batchId, int created, int matched, int skipped) {
}
