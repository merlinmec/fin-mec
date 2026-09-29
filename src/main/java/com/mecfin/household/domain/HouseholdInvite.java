package com.mecfin.household.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Convite para entrar num household (Fase 17). Só o hash do token é persistido; o convite é de
 * uso único, expira e fica preso ao e-mail convidado — encaminhar o link para outra pessoa não
 * adianta, ela precisaria entrar com a conta daquele e-mail.
 */
@Entity
@Table(name = "household_invites")
public class HouseholdInvite {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "household_id", nullable = false, updatable = false)
    private UUID householdId;

    @Column(name = "email", nullable = false, updatable = false)
    private String email;

    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    private String tokenHash;

    @Column(name = "invited_by")
    private UUID invitedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "accepted_by")
    private UUID acceptedBy;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected HouseholdInvite() {
    }

    public HouseholdInvite(UUID householdId, String email, String tokenHash, UUID invitedBy, Instant now,
            Instant expiresAt) {
        this.householdId = householdId;
        this.email = email;
        this.tokenHash = tokenHash;
        this.invitedBy = invitedBy;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    public boolean isPending(Instant now) {
        return acceptedAt == null && revokedAt == null && expiresAt.isAfter(now);
    }

    public void accept(UUID userId, Instant now) {
        if (!isPending(now)) {
            throw new IllegalStateException("Convite não está pendente");
        }
        this.acceptedAt = now;
        this.acceptedBy = userId;
    }

    public void revoke(Instant now) {
        if (acceptedAt == null && revokedAt == null) {
            this.revokedAt = now;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getHouseholdId() {
        return householdId;
    }

    public String getEmail() {
        return email;
    }

    public UUID getInvitedBy() {
        return invitedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
