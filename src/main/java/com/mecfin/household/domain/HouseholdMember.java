package com.mecfin.household.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

/**
 * Vínculo entre um {@link Household} e um usuário. No MVP existe sempre
 * exatamente um membro por household (o próprio dono, papel OWNER, criado
 * junto do household no registro) — a tabela já suporta um segundo membro
 * (MEMBER) quando o convite for implementado.
 */
@Entity
@Table(name = "household_members", uniqueConstraints = @UniqueConstraint(columnNames = {"household_id", "user_id"}))
public class HouseholdMember {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "household_id", nullable = false, updatable = false)
    private UUID householdId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private HouseholdRole role;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    protected HouseholdMember() {
    }

    public HouseholdMember(UUID householdId, UUID userId, HouseholdRole role) {
        this.householdId = householdId;
        this.userId = userId;
        this.role = role;
        this.joinedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getHouseholdId() {
        return householdId;
    }

    public UUID getUserId() {
        return userId;
    }

    public HouseholdRole getRole() {
        return role;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }
}
