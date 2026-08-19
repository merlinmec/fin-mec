package com.mecfin.category.application;

import com.mecfin.shared.exception.NotFoundException;
import java.util.UUID;

public class CategoryNotFoundException extends NotFoundException {

    public CategoryNotFoundException(UUID id) {
        super("Categoria não encontrada: " + id);
    }
}
