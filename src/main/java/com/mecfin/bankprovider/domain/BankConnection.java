package com.mecfin.bankprovider.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Conexão de um household com uma instituição via provedor de integração bancária (Pluggy,
 * Belvo, ...). Referencia household e instituição só por id (nunca relação JPA), mesmo padrão
 * do resto do projeto. Criada por {@code BankProviderClient.createConnection} - até um adapter
 * concreto existir, nada popula esta tabela.
 *
 * {@code accessTokenEncrypted} é sempre cifrado pela aplicação (AES-GCM, chave fora do banco)
 * antes de chegar aqui - a responsabilidade de cifrar é do adapter concreto, nunca texto claro
 * nesta coluna.
 */
@Entity
@Table(name = "bank_connections")
public class BankConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "household_id", nullable = false, updatable = false)
    private UUID householdId;

    @Column(name = "institution_id", nullable = false, updatable = false)
    private UUID institutionId;

    @Column(name = "provider", nullable = false, length = 60, updatable = false)
    private String provider;

    @Column(name = "external_item_id", nullable = false, length = 255, updatable = false)
    private String externalItemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BankConnectionStatus status;

    @Column(name = "access_token_encrypted")
    private String accessTokenEncrypted;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    // Fase 16: motivo legível da última falha e a partir de que data importar o histórico.
    @Column(name = "last_error")
    private String lastError;

    @Column(name = "sync_from")
    private LocalDate syncFrom;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BankConnection() {
    }

    public BankConnection(
            UUID householdId, UUID institutionId, String provider, String externalItemId,
            String accessTokenEncrypted) {
        this.householdId = householdId;
        this.institutionId = institutionId;
        this.provider = provider;
        this.externalItemId = externalItemId;
        this.accessTokenEncrypted = accessTokenEncrypted;
        this.status = BankConnectionStatus.ACTIVE;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    // Chamado após uma sincronização bem-sucedida (getAccounts/getBalances/getTransactions) -
    // também limpa um estado ERROR anterior, já que sincronizar de novo com sucesso prova que
    // a conexão voltou a funcionar.
    public void markSynced(Instant syncedAt) {
        this.lastSyncedAt = syncedAt;
        this.status = BankConnectionStatus.ACTIVE;
        this.lastError = null;
        touch();
    }

    public void markError() {
        markError(null);
    }

    public void markError(String reason) {
        this.status = BankConnectionStatus.ERROR;
        this.lastError = truncate(reason);
        touch();
    }

    public void markExpired(String reason) {
        this.status = BankConnectionStatus.EXPIRED;
        this.lastError = truncate(reason);
        touch();
    }

    /** Reconexão bem-sucedida pelo widget: volta a ativa (as credenciais foram renovadas). */
    public void reactivate() {
        this.status = BankConnectionStatus.ACTIVE;
        this.lastError = null;
        touch();
    }

    public void startHistoryFrom(LocalDate from) {
        this.syncFrom = from;
        touch();
    }

    private static String truncate(String value) {
        return value == null || value.length() <= 255 ? value : value.substring(0, 255);
    }

    public String getLastError() {
        return lastError;
    }

    public LocalDate getSyncFrom() {
        return syncFrom;
    }

    // Token expirado/revogado pelo usuário no banco de origem - precisa reconectar (fluxo de
    // createConnection de novo), não é recuperável só tentando sincronizar de novo.
    public void markExpired() {
        this.status = BankConnectionStatus.EXPIRED;
        touch();
    }

    public void disconnect() {
        this.status = BankConnectionStatus.DISCONNECTED;
        touch();
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getHouseholdId() {
        return householdId;
    }

    public UUID getInstitutionId() {
        return institutionId;
    }

    public String getProvider() {
        return provider;
    }

    public String getExternalItemId() {
        return externalItemId;
    }

    public BankConnectionStatus getStatus() {
        return status;
    }

    public String getAccessTokenEncrypted() {
        return accessTokenEncrypted;
    }

    public Instant getLastSyncedAt() {
        return lastSyncedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
