package com.mecfin.identity.application;

import com.mecfin.identity.domain.RecoveryCode;
import com.mecfin.identity.domain.SecurityEventType;
import com.mecfin.identity.domain.User;
import com.mecfin.identity.infra.RecoveryCodeRepository;
import com.mecfin.identity.infra.UserRepository;
import com.mecfin.shared.exception.ConflictException;
import com.mecfin.shared.security.SecretCipher;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Autenticação em dois fatores por TOTP (Fase 14). Fluxo de ativação em duas etapas, como em
 * qualquer serviço sério: setup gera o segredo (ainda inativo) e o QR code; enable só liga o
 * 2FA depois que o usuário prova que o app autenticador gera o código certo - senão um QR mal
 * escaneado trancaria o usuário fora da própria conta.
 */
@Service
public class MfaService {

    public static final String ISSUER = "fin-mec";
    private static final int RECOVERY_CODE_COUNT = 10;
    private static final String RECOVERY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final UserRepository userRepository;
    private final RecoveryCodeRepository recoveryCodeRepository;
    private final SecretCipher cipher;
    private final SecurityEventService securityEvents;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public MfaService(UserRepository userRepository, RecoveryCodeRepository recoveryCodeRepository,
            SecretCipher cipher, SecurityEventService securityEvents, Clock clock) {
        this.userRepository = userRepository;
        this.recoveryCodeRepository = recoveryCodeRepository;
        this.cipher = cipher;
        this.securityEvents = securityEvents;
        this.clock = clock;
    }

    public record Setup(String secret, String otpauthUri) {
    }

    @Transactional
    public Setup setup(UUID userId) {
        User user = load(userId);
        if (user.isTotpEnabled()) {
            throw new ConflictException("A verificação em duas etapas já está ativa");
        }
        String secret = Totp.generateSecret(random);
        user.startTotpEnrollment(cipher.encrypt(secret));
        return new Setup(secret, Totp.otpauthUri(ISSUER, user.getEmail(), secret));
    }

    /** Liga o 2FA e devolve os códigos de recuperação - a única vez em que aparecem em claro. */
    @Transactional
    public List<String> enable(UUID userId, String code, ClientInfo client) {
        User user = load(userId);
        if (user.isTotpEnabled()) {
            throw new ConflictException("A verificação em duas etapas já está ativa");
        }
        if (user.getTotpSecretEncrypted() == null) {
            throw new IllegalArgumentException("Inicie a configuração do app autenticador antes de ativar");
        }
        OptionalLong step = Totp.verify(cipher.decrypt(user.getTotpSecretEncrypted()), code, clock.instant(), 0);
        if (step.isEmpty()) {
            throw new IllegalArgumentException("Código inválido - confira o horário do celular e tente de novo");
        }
        user.enableTotp(step.getAsLong());
        securityEvents.record(userId, SecurityEventType.MFA_ENABLED, client);
        return replaceRecoveryCodes(userId);
    }

    @Transactional
    public void disable(UUID userId, ClientInfo client) {
        User user = load(userId);
        user.disableTotp();
        recoveryCodeRepository.deleteAllByUserId(userId);
        securityEvents.record(userId, SecurityEventType.MFA_DISABLED, client);
    }

    @Transactional
    public List<String> regenerateRecoveryCodes(UUID userId, ClientInfo client) {
        if (!load(userId).isTotpEnabled()) {
            throw new IllegalArgumentException("Ative a verificação em duas etapas primeiro");
        }
        securityEvents.record(userId, SecurityEventType.RECOVERY_CODES_REGENERATED, client);
        return replaceRecoveryCodes(userId);
    }

    public long remainingRecoveryCodes(UUID userId) {
        return recoveryCodeRepository.countByUserIdAndUsedAtIsNull(userId);
    }

    /**
     * Confere o segundo fator: código de 6 dígitos do app (sem reuso dentro da janela) ou um
     * código de recuperação ainda não usado (que é "queimado" aqui).
     */
    @Transactional
    public boolean verifySecondFactor(UUID userId, String code, ClientInfo client) {
        User user = load(userId);
        if (!user.isTotpEnabled() || code == null) {
            return false;
        }
        String trimmed = code.strip();
        if (trimmed.matches("\\d{6}")) {
            OptionalLong step = Totp.verify(cipher.decrypt(user.getTotpSecretEncrypted()), trimmed, clock.instant(),
                    user.getTotpLastStep());
            step.ifPresent(user::recordTotpStep);
            return step.isPresent();
        }
        byte[] candidate = hash(normalizeRecoveryCode(trimmed)).getBytes(StandardCharsets.US_ASCII);
        for (RecoveryCode recovery : recoveryCodeRepository.findAllByUserIdAndUsedAtIsNull(userId)) {
            if (MessageDigest.isEqual(candidate, recovery.getCodeHash().getBytes(StandardCharsets.US_ASCII))) {
                recovery.markUsed();
                securityEvents.record(userId, SecurityEventType.RECOVERY_CODE_USED, client);
                return true;
            }
        }
        return false;
    }

    private List<String> replaceRecoveryCodes(UUID userId) {
        recoveryCodeRepository.deleteAllByUserId(userId);
        List<String> codes = new ArrayList<>(RECOVERY_CODE_COUNT);
        List<RecoveryCode> entities = new ArrayList<>(RECOVERY_CODE_COUNT);
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            String code = randomChunk(5) + "-" + randomChunk(5);
            codes.add(code);
            entities.add(new RecoveryCode(userId, hash(normalizeRecoveryCode(code))));
        }
        recoveryCodeRepository.saveAll(entities);
        return codes;
    }

    // Alfabeto sem 0/O/1/I: o código é digitado a mão, então nada de caractere ambíguo.
    private String randomChunk(int length) {
        StringBuilder out = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            out.append(RECOVERY_ALPHABET.charAt(random.nextInt(RECOVERY_ALPHABET.length())));
        }
        return out.toString();
    }

    private static String normalizeRecoveryCode(String code) {
        return code.replace("-", "").replace(" ", "").toUpperCase(Locale.ROOT);
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }

    private User load(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> new IllegalStateException("Usuário não encontrado"));
    }
}
