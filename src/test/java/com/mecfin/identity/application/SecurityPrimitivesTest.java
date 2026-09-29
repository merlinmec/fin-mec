package com.mecfin.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mecfin.shared.security.SecretCipher;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/** TOTP (vetores do RFC 6238), base32, AES-GCM e política de senha. */
class SecurityPrimitivesTest {

    // Segredo do apêndice B do RFC 6238 (modo SHA1): "12345678901234567890" em ASCII.
    private static final byte[] RFC_KEY = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
    private static final String RFC_SECRET = Base32.encode(RFC_KEY);

    @Test
    void totpMatchesRfc6238TestVectors() {
        assertThat(Totp.code(RFC_KEY, Totp.step(Instant.ofEpochSecond(59)), 8)).isEqualTo("94287082");
        assertThat(Totp.code(RFC_KEY, Totp.step(Instant.ofEpochSecond(1111111109)), 8)).isEqualTo("07081804");
        assertThat(Totp.code(RFC_KEY, Totp.step(Instant.ofEpochSecond(1234567890)), 8)).isEqualTo("89005924");
        assertThat(Totp.code(RFC_KEY, Totp.step(Instant.ofEpochSecond(20000000000L)), 8)).isEqualTo("65353130");
    }

    @Test
    void totpVerifyAcceptsAdjacentStepAndRejectsReuse() {
        Instant now = Instant.ofEpochSecond(1_800_000_000L);
        long step = Totp.step(now);
        String previousStepCode = Totp.code(RFC_KEY, step - 1, 6);

        assertThat(Totp.verify(RFC_SECRET, previousStepCode, now, 0)).hasValue(step - 1);
        // mesmo código de novo, com esse passo já usado: recusado (anti-replay)
        assertThat(Totp.verify(RFC_SECRET, previousStepCode, now, step - 1)).isEmpty();
        // dois passos atrás: fora da janela
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_KEY, step - 2, 6), now, 0)).isEmpty();
        assertThat(Totp.verify(RFC_SECRET, "12345", now, 0)).isEmpty();
        assertThat(Totp.verify(RFC_SECRET, "abcdef", now, 0)).isEmpty();
    }

    @Test
    void base32RoundTripsAndGeneratedSecretsAreUnique() {
        byte[] data = {0, 1, 2, (byte) 250, (byte) 255, 42, 7};
        assertThat(Base32.decode(Base32.encode(data))).isEqualTo(data);
        assertThat(Base32.encode("foobar".getBytes(StandardCharsets.US_ASCII))).isEqualTo("MZXW6YTBOI");

        SecureRandom random = new SecureRandom();
        String secret = Totp.generateSecret(random);
        assertThat(secret).hasSize(32).matches("[A-Z2-7]+");
        assertThat(Totp.generateSecret(random)).isNotEqualTo(secret);
    }

    @Test
    void otpauthUriIsWhatAuthenticatorAppsExpect() {
        assertThat(Totp.otpauthUri("fin-mec", "joão@example.com", "ABC"))
                .isEqualTo("otpauth://totp/fin-mec:jo%C3%A3o%40example.com?secret=ABC&issuer=fin-mec"
                        + "&algorithm=SHA1&digits=6&period=30");
    }

    @Test
    void secretCipherRoundTripsUsesRandomIvAndDetectsTampering() {
        SecretCipher cipher = new SecretCipher(Base64.getEncoder().encodeToString(new byte[32]));

        String first = cipher.encrypt("segredo");
        String second = cipher.encrypt("segredo");

        assertThat(first).isNotEqualTo(second);
        assertThat(cipher.decrypt(first)).isEqualTo("segredo");

        byte[] tampered = Base64.getDecoder().decode(first);
        tampered[tampered.length - 1] ^= 1;
        assertThatThrownBy(() -> cipher.decrypt(Base64.getEncoder().encodeToString(tampered)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new SecretCipher(Base64.getEncoder().encodeToString(new byte[16])))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void passwordPolicyRejectsWeakPatternsButAcceptsPassphrases() {
        PasswordPolicy policy = new PasswordPolicy();

        assertThatThrownBy(() -> policy.validate("a@b.com", "Password123")).isInstanceOf(WeakPasswordException.class);
        assertThatThrownBy(() -> policy.validate("a@b.com", "aaaaaaaaaa")).isInstanceOf(WeakPasswordException.class);
        assertThatThrownBy(() -> policy.validate("a@b.com", "abababababab")).isInstanceOf(WeakPasswordException.class);
        assertThatThrownBy(() -> policy.validate("a@b.com", "cdefghijklmn")).isInstanceOf(WeakPasswordException.class);
        assertThatThrownBy(() -> policy.validate("maria.silva@x.com", "maria.silva2026"))
                .isInstanceOf(WeakPasswordException.class);

        policy.validate("maria.silva@x.com", "cavalo correto bateria");
        policy.validate("a@b.com", "s3cret1234");
    }
}
