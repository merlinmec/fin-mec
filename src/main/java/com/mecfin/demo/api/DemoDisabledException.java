package com.mecfin.demo.api;

import com.mecfin.shared.exception.NotFoundException;

/** Fora do deploy da demonstração, a rota de visitante simplesmente não existe. */
public class DemoDisabledException extends NotFoundException {

    public DemoDisabledException() {
        super("Demonstração desligada neste servidor.");
    }
}
