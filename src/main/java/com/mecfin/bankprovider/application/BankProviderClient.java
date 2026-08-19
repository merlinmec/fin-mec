package com.mecfin.bankprovider.application;

import java.time.Instant;
import java.util.List;

/**
 * Porta (padrão hexagonal) para integração bancária real. Único ponto de acoplamento a um
 * provedor concreto (Pluggy, Belvo, ...) do projeto inteiro - nenhum outro módulo depende do
 * provedor, só desta interface.
 *
 * <p><b>Sem implementação ainda</b> (decisão explícita do usuário ao retomar a Fase 9): nenhum
 * adapter concreto, nenhum {@code @Service}/{@code @Component} implementa esta porta hoje, e
 * nenhum outro módulo a injeta. O módulo {@code bankprovider} existe só como fronteira pronta -
 * schema ({@code bank_institutions}/{@code bank_connections}) e esta interface -, sem
 * "integração falsa" vazando pro resto do sistema. Quando uma conta/credencial de sandbox
 * (Pluggy é o provedor recomendado, ver seção H do doc de arquitetura) estiver disponível, um
 * {@code PluggyBankProviderAdapter} em {@code com.mecfin.bankprovider.infra} implementa esta
 * porta e é registrado como {@code @Service}.
 *
 * <p>Sincronização (quando implementada) deve ser idempotente: reprocessar a mesma conexão
 * nunca duplica instituição, conexão ou lançamento - ver
 * {@code BankConnectionRepository.findByProviderAndExternalItemId} e
 * {@code ExternalBankTransaction.externalTransactionId}.
 */
public interface BankProviderClient {

    List<ExternalInstitution> listInstitutions();

    ExternalConnectionResult createConnection(String institutionProviderCode, String publicToken);

    List<ExternalBankAccount> getAccounts(String externalItemId);

    List<ExternalBalance> getBalances(String externalItemId);

    List<ExternalBankTransaction> getTransactions(String externalItemId, Instant since);

    void disconnect(String externalItemId);
}
