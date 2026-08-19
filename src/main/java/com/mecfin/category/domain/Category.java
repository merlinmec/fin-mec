package com.mecfin.category.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Categoria de receita/despesa. Referencia household e parent só por id
 * (nunca relação JPA) — mesmo motivo de {@code Account}: mantém os módulos
 * financeiros desacoplados no nível de persistência.
 *
 * {@code householdId} nulo identifica uma categoria padrão do sistema
 * (seed em {@code V5__seed_default_categories.sql}), visível — mas não
 * editável nem excluível — por todo household. Só o construtor de categoria
 * de usuário existe aqui; categorias de sistema nascem via migration.
 */
@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "household_id", updatable = false)
    private UUID householdId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private CategoryType type;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "color", length = 7)
    private String color;

    @Column(name = "icon", length = 50)
    private String icon;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Category() {
    }

    public Category(UUID householdId, String name, CategoryType type, UUID parentId, String color, String icon) {
        this.householdId = Objects.requireNonNull(householdId, "householdId é obrigatório para categoria de usuário");
        this.name = name;
        this.type = type;
        this.parentId = parentId;
        this.color = color;
        this.icon = icon;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String name, CategoryType type, UUID parentId, String color, String icon) {
        this.name = name;
        this.type = type;
        this.parentId = parentId;
        this.color = color;
        this.icon = icon;
        touch();
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
        touch();
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    public boolean isSystemDefault() {
        return householdId == null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getHouseholdId() {
        return householdId;
    }

    public String getName() {
        return name;
    }

    public CategoryType getType() {
        return type;
    }

    public UUID getParentId() {
        return parentId;
    }

    public String getColor() {
        return color;
    }

    public String getIcon() {
        return icon;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
