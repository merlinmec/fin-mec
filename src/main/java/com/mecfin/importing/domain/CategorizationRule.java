package com.mecfin.importing.domain;

import com.mecfin.importing.domain.DescriptionNormalizer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * "Se a descrição contém {@code pattern}, use esta categoria (e esta tag)". O padrão é guardado
 * normalizado (ver DescriptionNormalizer), então "PADARIA", "Padaria" e "padária" são a mesma
 * regra. Com várias regras casando, vence a de padrão mais longo — a mais específica.
 */
@Entity
@Table(name = "categorization_rules")
public class CategorizationRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "household_id", nullable = false, updatable = false)
    private UUID householdId;

    @Column(name = "pattern", nullable = false, length = 100)
    private String pattern;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "tag_id")
    private UUID tagId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CategorizationRule() {
    }

    public CategorizationRule(UUID householdId, String pattern, UUID categoryId, UUID tagId) {
        this.householdId = householdId;
        update(pattern, categoryId, tagId);
        this.createdAt = Instant.now();
    }

    public void update(String pattern, UUID categoryId, UUID tagId) {
        String normalized = DescriptionNormalizer.normalize(pattern);
        if (normalized.length() < 2) {
            throw new IllegalArgumentException("O texto da regra precisa ter ao menos 2 letras");
        }
        this.pattern = normalized;
        this.categoryId = categoryId;
        this.tagId = tagId;
    }

    public boolean matches(String normalizedDescription) {
        return (" " + normalizedDescription + " ").contains(" " + pattern + " ")
                || normalizedDescription.contains(pattern);
    }

    public UUID getId() {
        return id;
    }

    public String getPattern() {
        return pattern;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public UUID getTagId() {
        return tagId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
