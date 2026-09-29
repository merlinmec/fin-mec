package com.mecfin.bankprovider.application;

/**
 * Uma conexão no provedor. health já vem traduzido para o vocabulário do produto; error é a
 * mensagem legível quando não está saudável.
 */
public record ExternalItem(
        String id,
        String clientUserId,
        Health health,
        String error,
        String institutionCode,
        String institutionName,
        String institutionImageUrl,
        String institutionColor) {

    public enum Health {
        /** Sincronizado (ou sincronizando): dados disponíveis. */
        OK,
        /** Falhou mas pode se recuperar sozinho na próxima tentativa. */
        ERROR,
        /** Precisa do usuário: credencial inválida, consentimento revogado, MFA pendente. */
        NEEDS_RECONNECT
    }
}
