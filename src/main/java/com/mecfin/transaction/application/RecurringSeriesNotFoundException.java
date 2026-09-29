package com.mecfin.transaction.application;

import com.mecfin.shared.exception.NotFoundException;
import java.util.UUID;

public class RecurringSeriesNotFoundException extends NotFoundException {

    public RecurringSeriesNotFoundException(UUID id) {
        super("Lançamento fixo não encontrado: " + id);
    }
}
