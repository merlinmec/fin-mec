package com.mecfin.identity.application;

import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Regras de senha além do tamanho (10-72, validado no DTO). Segue a linha do NIST SP 800-63B:
 * sem exigir "maiúscula + símbolo" (que empurra para senhas previsíveis como "Senha@2026"),
 * mas recusando as senhas comprovadamente fracas - lista de senhas comuns, sequências triviais e
 * senha que contém o próprio e-mail.
 */
@Component
public class PasswordPolicy {

    // Senhas com 10+ caracteres que aparecem no topo de vazamentos públicos (incluindo as
    // variações brasileiras mais comuns).
    private static final Set<String> COMMON = Set.of(
            "1234567890", "0123456789", "12345678910", "123456789a", "a123456789", "qwertyuiop",
            "1q2w3e4r5t", "1q2w3e4r5t6y", "qwerty1234", "qwerty12345", "password12", "password123",
            "password1234", "senha12345", "senha123456", "minhasenha", "minhasenha1", "iloveyou12",
            "abcdefghij", "abc1234567", "abcd123456", "asdfghjkl1", "1111111111", "0000000000",
            "9876543210", "brasil2024", "brasil2025", "brasil2026", "flamengo10", "corinthians",
            "palmeiras1", "saopaulo10", "trustno1234", "letmein123", "welcome123", "adminadmin",
            "administrator", "changeme123", "p@ssw0rd123", "passw0rd12", "fin-mec123", "finmec1234");

    public void validate(String email, String password) {
        String lower = password.toLowerCase(Locale.ROOT);
        if (COMMON.contains(lower)) {
            throw new WeakPasswordException("Essa senha é muito comum e aparece em vazamentos públicos");
        }
        if (lower.chars().distinct().count() <= 2) {
            throw new WeakPasswordException("A senha precisa ter mais variedade de caracteres");
        }
        if (isSequential(lower)) {
            throw new WeakPasswordException("A senha não pode ser uma sequência simples");
        }
        String localPart = email.toLowerCase(Locale.ROOT).split("@", 2)[0];
        if (localPart.length() >= 4 && lower.contains(localPart)) {
            throw new WeakPasswordException("A senha não pode conter o seu e-mail");
        }
    }

    // "abcdefghij", "0123456789", "9876543210": diferença constante de +1 ou -1 entre vizinhos.
    private static boolean isSequential(String value) {
        int step = value.charAt(1) - value.charAt(0);
        if (Math.abs(step) != 1) {
            return false;
        }
        for (int i = 2; i < value.length(); i++) {
            if (value.charAt(i) - value.charAt(i - 1) != step) {
                return false;
            }
        }
        return true;
    }
}
