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
 * pertencem a um household, nunca diretamente a um usuário — por isso convidar um
 * segundo membro (ex.: casal, Fase 17) não exigiu migrar nenhum dado. Todo usuário ganha um
 * household próprio no registro (ver {@link com.mecfin.household.application.HouseholdService}).
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

    public void rename(String name) {
        this.name = name;
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
