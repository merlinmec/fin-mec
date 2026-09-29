package com.mecfin.importing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Registro de um arquivo importado: o que aconteceu com ele e se já foi desfeito. */
@Entity
@Table(name = "import_batches")
public class ImportBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "household_id", nullable = false, updatable = false)
    private UUID householdId;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "file_name", nullable = false, updatable = false)
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(name = "format", nullable = false, length = 10, updatable = false)
    private ImportFormat format;

    @Column(name = "created_count", nullable = false)
    private int createdCount;

    @Column(name = "matched_count", nullable = false)
    private int matchedCount;

    @Column(name = "skipped_count", nullable = false)
    private int skippedCount;

    @Column(name = "undone_at")
    private Instant undoneAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ImportBatch() {
    }

    public ImportBatch(UUID householdId, UUID accountId, String fileName, ImportFormat format) {
        this.householdId = householdId;
        this.accountId = accountId;
        this.fileName = fileName;
        this.format = format;
        this.createdAt = Instant.now();
    }

    public void record(int created, int matched, int skipped) {
        this.createdCount = created;
        this.matchedCount = matched;
        this.skippedCount = skipped;
    }

    public void markUndone() {
        this.undoneAt = Instant.now();
    }

    public boolean isUndone() {
        return undoneAt != null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getHouseholdId() {
        return householdId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getFileName() {
        return fileName;
    }

    public ImportFormat getFormat() {
        return format;
    }

    public int getCreatedCount() {
        return createdCount;
    }

    public int getMatchedCount() {
        return matchedCount;
    }

    public int getSkippedCount() {
        return skippedCount;
    }

    public Instant getUndoneAt() {
        return undoneAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
