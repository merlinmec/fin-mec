package com.mecfin.identity.application;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.OptionalLong;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * TOTP (RFC 6238) com os parâmetros que todo app autenticador usa por padrão: HMAC-SHA1,
 * 6 dígitos, passo de 30s. Implementado aqui (≈60 linhas) em vez de puxar uma dependência
 * para um algoritmo pequeno e bem especificado; os vetores de teste do RFC estão em TotpTest.
 */
final class Totp {

    static final int PERIOD_SECONDS = 30;
    static final int DIGITS = 6;
    // Aceita o passo anterior e o seguinte além do atual: tolera ~30s de relógio dessincronizado.
    private static final int WINDOW = 1;
    private static final int SECRET_BYTES = 20;

    private Totp() {
    }

    static String generateSecret(SecureRandom random) {
        byte[] secret = new byte[SECRET_BYTES];
        random.nextBytes(secret);
        return Base32.encode(secret);
    }

    static long step(Instant instant) {
        return instant.getEpochSecond() / PERIOD_SECONDS;
    }

    static String code(byte[] key, long step, int digits) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(new byte[] {
                (byte) (step >>> 56), (byte) (step >>> 48), (byte) (step >>> 40), (byte) (step >>> 32),
                (byte) (step >>> 24), (byte) (step >>> 16), (byte) (step >>> 8), (byte) step});
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            int otp = binary % (int) Math.pow(10, digits);
            return String.format("%0" + digits + "d", otp);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA1 indisponível", e);
        }
    }

    /**
     * Verifica o código e devolve o passo em que ele casou, ou vazio. Passos até
     * {@code lastUsedStep} (inclusive) são recusados: o mesmo código não serve duas vezes.
     */
    static OptionalLong verify(String base32Secret, String code, Instant now, long lastUsedStep) {
        if (code == null || !code.matches("\\d{" + DIGITS + "}")) {
            return OptionalLong.empty();
        }
        byte[] key = Base32.decode(base32Secret);
        long current = step(now);
        for (long candidate = current - WINDOW; candidate <= current + WINDOW; candidate++) {
            if (candidate <= lastUsedStep) {
                continue;
            }
            byte[] expected = code(key, candidate, DIGITS).getBytes(StandardCharsets.US_ASCII);
            if (MessageDigest.isEqual(expected, code.getBytes(StandardCharsets.US_ASCII))) {
                return OptionalLong.of(candidate);
            }
        }
        return OptionalLong.empty();
    }

    static String otpauthUri(String issuer, String account, String base32Secret) {
        String label = encode(issuer) + ":" + encode(account);
        return "otpauth://totp/" + label + "?secret=" + base32Secret + "&issuer=" + encode(issuer)
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + PERIOD_SECONDS;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
