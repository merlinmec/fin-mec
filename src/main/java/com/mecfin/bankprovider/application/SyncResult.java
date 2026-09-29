package com.mecfin.bankprovider.application;

import com.mecfin.bankprovider.domain.BankConnectionStatus;

/** Resumo de uma sincronização: lançamentos criados, previstos efetivados, linhas ignoradas. */
public record SyncResult(BankConnectionStatus status, int created, int matched, int skipped, String message) {
}
