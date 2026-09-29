package com.mecfin.identity.api;

// secret em base32 para digitar a mão; otpauthUri vira o QR code no cliente.
public record MfaSetupResponse(String secret, String otpauthUri) {
}
