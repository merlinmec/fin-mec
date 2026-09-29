package com.mecfin.shared.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Cifra simétrica (AES-256-GCM) para segredos que o servidor precisa ler de volta - hoje só o
 * segredo TOTP do 2FA (senha continua com hash Argon2, que é unidirecional). A chave vem de
 * {@code mecfin.security.encryption-key} (variável MECFIN_ENCRYPTION_KEY), nunca do banco: um
 * dump do banco sozinho não expõe os segredos.
 *
 * Formato armazenado: base64(IV de 12 bytes || ciphertext+tag). GCM autentica o conteúdo -
 * valor adulterado no banco falha na decifragem em vez de devolver lixo.
 */
@Component
public class SecretCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public SecretCipher(@Value("${mecfin.security.encryption-key}") String base64Key) {
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("mecfin.security.encryption-key não é base64 válido", e);
        }
        if (raw.length != 32) {
            throw new IllegalStateException("mecfin.security.encryption-key precisa ter 32 bytes (AES-256), tem "
                    + raw.length + " - gere com: openssl rand -base64 32");
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[IV_BYTES + encrypted.length];
            System.arraycopy(iv, 0, out, 0, IV_BYTES);
            System.arraycopy(encrypted, 0, out, IV_BYTES, encrypted.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao cifrar segredo", e);
        }
    }

    public String decrypt(String stored) {
        try {
            byte[] in = Base64.getDecoder().decode(stored);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, in, 0, IV_BYTES));
            return new String(cipher.doFinal(in, IV_BYTES, in.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Falha ao decifrar segredo (chave errada ou valor adulterado)", e);
        }
    }
}
