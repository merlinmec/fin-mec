package com.mecfin.category.api;

import com.mecfin.category.domain.Category;
import com.mecfin.category.domain.CategoryType;
import java.time.Instant;
import java.util.UUID;

public record CategoryResponse(
        UUID id,
        String name,
        CategoryType type,
        UUID parentId,
        String color,
        String icon,
        boolean systemDefault,
        Instant createdAt,
        Instant updatedAt) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getType(),
                category.getParentId(),
                category.getColor(),
                category.getIcon(),
                category.isSystemDefault(),
                category.getCreatedAt(),
                category.getUpdatedAt());
    }
}
