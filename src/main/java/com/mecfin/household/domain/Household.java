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
 * Espaço financeiro compartilhado. Contas, categorias, orçamentos e lançamentos
 * pertencem a um household, nunca diretamente a um usuário — isso permite, no
 * futuro, convidar um segundo membro para o mesmo espaço (ex.: casal) sem
 * migração de schema. No MVP todo usuário ganha um household próprio
 * automaticamente no registro (ver {@link com.mecfin.household.application.HouseholdService}).
 */
@Entity
@Table(name = "households")
public class Household {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Household() {
    }

    public Household(String name) {
        this.name = name;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
