package com.mecfin.bankprovider.application;

// Retorno de BankProviderClient.createConnection() - externalItemId identifica a conexão no
// provedor (chave de idempotência, ver BankConnectionRepository.findByProviderAndExternalItemId).
// accessTokenEncrypted já vem cifrado pelo adapter (AES-GCM, chave fora do banco) - a porta
// nunca recebe nem repassa token em texto claro.
public record ExternalConnectionResult(String externalItemId, String accessTokenEncrypted) {
}
