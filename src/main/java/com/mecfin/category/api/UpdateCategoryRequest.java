package com.mecfin.category.api;

import com.mecfin.category.domain.CategoryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record UpdateCategoryRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull CategoryType type,
        UUID parentId,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "cor deve estar no formato hexadecimal #RRGGBB") String color,
        @Size(max = 50) String icon) {
}
