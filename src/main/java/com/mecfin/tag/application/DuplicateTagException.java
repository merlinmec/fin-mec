package com.mecfin.tag.application;

import com.mecfin.shared.exception.ConflictException;

public class DuplicateTagException extends ConflictException {

    public DuplicateTagException(String name) {
        super("Já existe uma tag com o nome \"" + name + "\"");
    }
}
