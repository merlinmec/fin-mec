package com.mecfin.identity.api;

// Resposta 202 do primeiro passo do login quando a conta tem 2FA: a sessão ainda NÃO está
// autenticada, o cliente precisa mandar o código em POST /auth/login/mfa.
public record LoginResult(boolean mfaRequired) {
}
