package com.mecfin.identity.application;

import java.time.Instant;

/** Gera códigos TOTP válidos em testes de integração (Totp é package-private de propósito). */
public final class TotpTestCodes {

    private TotpTestCodes() {
    }

    /** Código do passo atual + {@code offset} (o servidor aceita -1..+1). */
    public static String code(String base32Secret, int offset) {
        return Totp.code(Base32.decode(base32Secret), Totp.step(Instant.now()) + offset, Totp.DIGITS);
    }
}
