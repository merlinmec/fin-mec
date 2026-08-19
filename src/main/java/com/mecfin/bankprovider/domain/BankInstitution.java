package com.mecfin.bankprovider.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Catálogo de instituições (bancos) suportadas pelo provedor de integração bancária. Sem
 * {@code household_id} - é um catálogo global do provedor, não um dado do usuário (mesmo
 * espírito de {@code Category} com {@code householdId} nulo, mas aqui é sempre global).
 *
 * Populado por {@code BankProviderClient.listInstitutions()} quando um adapter concreto
 * (Pluggy/Belvo) existir - até lá, a tabela fica vazia. Ver
 * {@code com.mecfin.bankprovider.application.BankProviderClient}.
 */
@Entity
@Table(name = "bank_institutions")
public class BankInstitution {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "provider_code", nullable = false, length = 60, unique = true)
    private String providerCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BankInstitution() {
    }

    public BankInstitution(String name, String providerCode) {
        this.name = name;
        this.providerCode = providerCode;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getProviderCode() {
        return providerCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
