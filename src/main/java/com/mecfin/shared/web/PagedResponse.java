package com.mecfin.shared.web;

import java.util.List;
import org.springframework.data.domain.Page;

// Envelope de paginação para a API - nunca devolver org.springframework.data.domain.Page
// direto num controller (Spring Boot recomenda contra isso: serialização de PageImpl não é
// um contrato estável entre versões).
public record PagedResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PagedResponse<T> from(Page<T> page) {
        return new PagedResponse<>(
                page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
