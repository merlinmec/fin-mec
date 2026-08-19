package com.mecfin.bankprovider.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// Sem service/adapter ainda (ver BankProviderClient) - testa só as transições de status do
// domínio diretamente, mesmo espírito de um teste unitário de entidade sem dependências
// externas a mockar.
class BankConnectionTest {

    private BankConnection newConnection() {
        return new BankConnection(
                UUID.randomUUID(), UUID.randomUUID(), "pluggy", "item-123", "cipher-text");
    }

    @Test
    void newConnectionStartsActive() {
        BankConnection connection = newConnection();

        assertThat(connection.getStatus()).isEqualTo(BankConnectionStatus.ACTIVE);
        assertThat(connection.getLastSyncedAt()).isNull();
    }

    @Test
    void markSyncedSetsLastSyncedAtAndClearsError() {
        BankConnection connection = newConnection();
        connection.markError();

        Instant syncedAt = Instant.now();
        connection.markSynced(syncedAt);

        assertThat(connection.getStatus()).isEqualTo(BankConnectionStatus.ACTIVE);
        assertThat(connection.getLastSyncedAt()).isEqualTo(syncedAt);
    }

    @Test
    void markErrorSetsErrorStatus() {
        BankConnection connection = newConnection();

        connection.markError();

        assertThat(connection.getStatus()).isEqualTo(BankConnectionStatus.ERROR);
    }

    @Test
    void markExpiredSetsExpiredStatus() {
        BankConnection connection = newConnection();

        connection.markExpired();

        assertThat(connection.getStatus()).isEqualTo(BankConnectionStatus.EXPIRED);
    }

    @Test
    void disconnectSetsDisconnectedStatus() {
        BankConnection connection = newConnection();

        connection.disconnect();

        assertThat(connection.getStatus()).isEqualTo(BankConnectionStatus.DISCONNECTED);
    }
}
