package com.mecfin.bankprovider.application;

import java.time.LocalDate;
import java.util.List;

/**
 * Porta (padrão hexagonal) para integração bancária. Único ponto de acoplamento a um provedor
 * concreto no projeto inteiro — hoje implementada por PluggyClient (Fase 16).
 *
 * Revisada na Fase 16 para o fluxo real dos agregadores de Open Finance (a versão da Fase 9 foi
 * desenhada antes de existir um provedor): o banco é conectado por um widget do provedor no
 * navegador, autorizado por um connect token de curta duração gerado aqui; o backend só recebe o
 * id do "item" (a conexão) e a partir dele lê contas e transações.
 */
public interface BankProviderClient {

    String providerName();

    /** false = credenciais do provedor não configuradas; a funcionalidade fica desligada. */
    boolean isConfigured();

    /**
     * Token de curta duração para o widget do provedor. clientUserId carimba o item criado com o
     * dono — é a checagem que impede um usuário de registrar a conexão de outro.
     * existingItemId != null = fluxo de reconexão (atualizar credenciais do mesmo item).
     */
    String createConnectToken(String clientUserId, String existingItemId, String webhookUrl);

    ExternalItem getItem(String itemId);

    /** Só contas bancárias (corrente/poupança); cartão de crédito tem módulo próprio no produto. */
    List<ExternalBankAccount> getBankAccounts(String itemId);

    /** Transações da conta a partir de uma data (inclusive), todas as páginas. */
    List<ExternalBankTransaction> getTransactions(String externalAccountId, LocalDate from);

    void deleteItem(String itemId);
}
