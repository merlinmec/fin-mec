package com.mecfin.tag.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Mesmo payload para criar e editar (tag só tem nome e cor).
public record TagRequest(
        @NotBlank @Size(max = 50) String name,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "cor deve estar no formato hexadecimal #RRGGBB") String color) {
}
