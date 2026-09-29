package com.mecfin.tag.application;

import com.mecfin.shared.exception.NotFoundException;
import java.util.UUID;

public class TagNotFoundException extends NotFoundException {

    public TagNotFoundException(UUID id) {
        super("Tag não encontrada: " + id);
    }
}
