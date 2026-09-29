package com.mecfin.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // ---- Fase 14: segurança da conta ----

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    // Ver SessionValidityFilter: toda sessão guarda o carimbo do login; mudou, a sessão cai.
    @Column(name = "security_stamp", nullable = false)
    private UUID securityStamp;

    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    // Cifrado por SecretCipher. Preenchido já no setup do 2FA, mas só vale depois de
    // totpEnabled=true (o usuário provou que configurou o app autenticador).
    @Column(name = "totp_secret_encrypted", length = 255)
    private String totpSecretEncrypted;

    @Column(name = "totp_enabled", nullable = false)
    private boolean totpEnabled;

    @Column(name = "totp_last_step", nullable = false)
    private long totpLastStep;

    protected User() {
    }

    public User(String email, String passwordHash) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.createdAt = Instant.now();
        this.securityStamp = UUID.randomUUID();
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /** Conta uma falha; devolve true se esta falha acabou de bloquear a conta. */
    public boolean registerFailedLogin(int maxAttempts, Duration lockDuration, Instant now) {
        failedLoginAttempts++;
        if (failedLoginAttempts >= maxAttempts) {
            failedLoginAttempts = 0;
            lockedUntil = now.plus(lockDuration);
            return true;
        }
        return false;
    }

    public void resetFailedLogins() {
        failedLoginAttempts = 0;
        lockedUntil = null;
    }

    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
        this.passwordChangedAt = Instant.now();
        rotateSecurityStamp();
    }

    public void rotateSecurityStamp() {
        this.securityStamp = UUID.randomUUID();
    }

    public void startTotpEnrollment(String encryptedSecret) {
        this.totpSecretEncrypted = encryptedSecret;
        this.totpEnabled = false;
        this.totpLastStep = 0;
    }

    public void enableTotp(long verifiedStep) {
        this.totpEnabled = true;
        this.totpLastStep = verifiedStep;
        rotateSecurityStamp();
    }

    public void disableTotp() {
        this.totpEnabled = false;
        this.totpSecretEncrypted = null;
        this.totpLastStep = 0;
        rotateSecurityStamp();
    }

    public void recordTotpStep(long step) {
        this.totpLastStep = step;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public UUID getSecurityStamp() {
        return securityStamp;
    }

    public Instant getPasswordChangedAt() {
        return passwordChangedAt;
    }

    public String getTotpSecretEncrypted() {
        return totpSecretEncrypted;
    }

    public boolean isTotpEnabled() {
        return totpEnabled;
    }

    public long getTotpLastStep() {
        return totpLastStep;
    }
}
