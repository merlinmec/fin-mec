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

    // Fase 16: logo e cor do banco, vindos do provedor (atualizados a cada conexão).
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "primary_color", length = 20)
    private String primaryColor;

    public void updateBranding(String name, String imageUrl, String primaryColor) {
        if (name != null && !name.isBlank()) {
            this.name = name.length() <= 120 ? name : name.substring(0, 120);
        }
        this.imageUrl = imageUrl != null && imageUrl.length() <= 500 && imageUrl.startsWith("https://") ? imageUrl : null;
        this.primaryColor = primaryColor != null && primaryColor.matches("#?[0-9A-Fa-f]{3,8}")
                ? (primaryColor.startsWith("#") ? primaryColor : "#" + primaryColor)
                : null;
        this.updatedAt = Instant.now();
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getPrimaryColor() {
        return primaryColor;
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
